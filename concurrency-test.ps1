# concurrency-test.ps1
#
# Fires multiple booking requests at the SAME seat, at (as close as possible
# to) the exact same instant, using PowerShell background jobs so the
# requests are genuinely in flight concurrently rather than one after another.
#
# This proves the @Version optimistic locking in SeatReservationService
# actually prevents double-booking under real concurrent load: exactly ONE
# request should end up CONFIRMED, and all the others FAILED with reason
# SEAT_ALREADY_BOOKED.
#
# Usage:
#   .\concurrency-test.ps1
#   .\concurrency-test.ps1 -EventId 2 -SeatNumber "S3" -Requests 10
#
# If PowerShell blocks the script from running, run this once first:
#   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

param(
    [int]$EventId = 1,
    [string]$SeatNumber = "S1",
    [int]$Requests = 5,
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host ""
Write-Host "Firing $Requests concurrent booking requests for event $EventId, seat $SeatNumber ..." -ForegroundColor Cyan
Write-Host ""

# Kick off all requests as background jobs almost simultaneously.
$jobs = @()
for ($i = 1; $i -le $Requests; $i++) {
    $userId = "concurrent-user-$i"
    $jobs += Start-Job -ScriptBlock {
        param($BaseUrl, $EventId, $SeatNumber, $UserId)
        $body = @{ eventId = $EventId; seatNumber = $SeatNumber; userId = $UserId } | ConvertTo-Json
        try {
            $response = Invoke-RestMethod -Uri "$BaseUrl/api/bookings" -Method Post -ContentType "application/json" -Body $body
            return $response
        } catch {
            return @{ error = $_.Exception.Message }
        }
    } -ArgumentList $BaseUrl, $EventId, $SeatNumber, $userId
}

Write-Host "All $Requests requests dispatched. Waiting for responses..." -ForegroundColor DarkGray
Wait-Job -Job $jobs | Out-Null

$bookingIds = @()
foreach ($job in $jobs) {
    $result = Receive-Job -Job $job
    if ($result.bookingId) {
        $bookingIds += $result.bookingId
    }
}
Remove-Job -Job $jobs

Write-Host ""
Write-Host "All bookings submitted (status PENDING). Polling until each one reaches a final state..." -ForegroundColor DarkGray

# Poll each booking repeatedly until it's CONFIRMED or FAILED (not just PENDING),
# instead of guessing with a fixed sleep - RabbitMQ processing time can vary,
# especially on a "cold" run right after the services start up.
$maxPollAttempts = 15
$pollIntervalSeconds = 1

$finalStatuses = @{}
for ($attempt = 1; $attempt -le $maxPollAttempts; $attempt++) {
    $allDone = $true
    foreach ($id in $bookingIds) {
        if (-not $finalStatuses.ContainsKey($id)) {
            $status = Invoke-RestMethod -Uri "$BaseUrl/api/bookings/$id" -Method Get
            if ($status.status -ne "PENDING") {
                $finalStatuses[$id] = $status
            } else {
                $allDone = $false
            }
        }
    }
    if ($allDone) { break }
    Start-Sleep -Seconds $pollIntervalSeconds
}

# Anything still not resolved after all retries is genuinely stuck - report it as PENDING rather than mislabeling it.
foreach ($id in $bookingIds) {
    if (-not $finalStatuses.ContainsKey($id)) {
        $finalStatuses[$id] = Invoke-RestMethod -Uri "$BaseUrl/api/bookings/$id" -Method Get
    }
}

# Now summarize.
Write-Host ""
Write-Host "===================== RESULTS =====================" -ForegroundColor Cyan

$confirmed = 0
$failed = 0
$stillPending = 0

foreach ($id in $bookingIds) {
    $status = $finalStatuses[$id]
    if ($status.status -eq "CONFIRMED") {
        $confirmed++
        Write-Host ("CONFIRMED  -> bookingId={0} userId={1}" -f $status.id, $status.userId) -ForegroundColor Green
    } elseif ($status.status -eq "FAILED") {
        $failed++
        Write-Host ("FAILED     -> bookingId={0} userId={1} reason={2}" -f $status.id, $status.userId, $status.failureReason) -ForegroundColor Red
    } else {
        $stillPending++
        Write-Host ("STILL PENDING -> bookingId={0} userId={1} (processing took longer than expected)" -f $status.id, $status.userId) -ForegroundColor Yellow
    }
}

Write-Host "===================================================="
Write-Host ""
Write-Host "Summary: $confirmed CONFIRMED, $failed FAILED, $stillPending STILL PENDING (out of $Requests requests for the same seat)" -ForegroundColor Yellow
if ($confirmed -eq 1 -and $failed -eq ($Requests - 1) -and $stillPending -eq 0) {
    Write-Host "Exactly one booking won the race, as expected. Optimistic locking worked correctly." -ForegroundColor Green
} else {
    Write-Host "Unexpected result - check that the seat started as AVAILABLE before running this script." -ForegroundColor Magenta
}
Write-Host ""