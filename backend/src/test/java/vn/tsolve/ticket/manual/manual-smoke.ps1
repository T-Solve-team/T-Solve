param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Password
)

$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[string]]::new()
$manualSession = $null
$manualCsrf = $null

function Assert-Manual($condition, [string]$label) {
    if (!$condition) { throw "FAILED: $label" }
    $checks.Add($label)
}

function Read-ManualJson($response) {
    # PowerShell can return application/problem+json as bytes instead of text (BUG-017).
    $body = if ($response.Content -is [byte[]]) {
        [System.Text.Encoding]::UTF8.GetString($response.Content)
    } else {
        [string]$response.Content
    }
    ConvertFrom-Json -InputObject $body
}

function Request-Manual([string]$path, [string]$method = 'Get', $body = $null, [string]$user = 'pm') {
    $headers = @{Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($user + ':' + $Password))}
    if ($manualCsrf) { $headers[$manualCsrf.headerName] = $manualCsrf.token }
    $arguments = @{
        Uri = $BaseUrl.TrimEnd('/') + $path
        Method = $method
        Headers = $headers
        WebSession = $manualSession
        SkipHttpErrorCheck = $true
        TimeoutSec = 10
    }
    if ($null -ne $body) {
        $arguments.Body = ConvertTo-Json -InputObject $body -Compress
        $arguments.ContentType = 'application/json'
    }
    Invoke-WebRequest @arguments
}

function testManualSwaggerCsrf_BUG_014 {
    $headers = @{Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('pm:' + $Password))}
    $response = Invoke-WebRequest ($BaseUrl.TrimEnd('/') + '/manual-review/csrf') -Headers $headers -SessionVariable script:manualSession
    $script:manualCsrf = Read-ManualJson $response
    Assert-Manual ($response.StatusCode -eq 200 -and $manualSession.Cookies.Count -gt 0) 'BUG-014: backend starts and supplies CSRF cookie'
}

testManualSwaggerCsrf_BUG_014
$schema = Invoke-RestMethod ($BaseUrl.TrimEnd('/') + '/v3/api-docs')

function testManualCsrfSchema_BUG_015 {
    $parameters = @($schema.paths.'/manual-review/csrf'.get.parameters).Where({$null -ne $_})
    Assert-Manual ($parameters.Count -eq 0) 'BUG-015: Swagger CSRF has no required query input'
}

testManualCsrfSchema_BUG_015
$paging = @($schema.paths.'/api/v1/review-queue'.get.parameters | ForEach-Object name)
Assert-Manual ('page' -in $paging -and 'size' -in $paging -and 'sort' -in $paging -and 'pageable' -notin $paging) 'BUG-016: Swagger paging parameters'

$response = Request-Manual '/manual-review/fixtures' 'Post'
Assert-Manual ($response.StatusCode -eq 200) 'CSRF-protected fixture creation'
$fixture = Read-ManualJson $response

$response = Request-Manual '/api/v1/reviews/approve' 'Post' @{ticketIds = @($fixture.approveId)}
Assert-Manual ($response.StatusCode -eq 200) 'Approve returns 200'
$state = Read-ManualJson (Request-Manual ('/manual-review/state/' + $fixture.approveId))
Assert-Manual ($state.ticket.status -eq 'PUBLISHED' -and $state.decisions.Count -eq 1 -and $state.audit.Count -eq 1 -and $state.notifications.Count -eq 0) 'Approve persists history/audit without email'

$response = Request-Manual '/api/v1/reviews/reject' 'Post' @{ticketIds = @($fixture.rejectId); reason = '   '}
Assert-Manual ($response.StatusCode -eq 400) 'Blank rejection reason returns 400'
$response = Request-Manual '/api/v1/reviews/reject' 'Post' @{ticketIds = @($fixture.rejectId); reason = 'Solution is incorrect'}
Assert-Manual ($response.StatusCode -eq 200) 'Reject returns 200'
$state = Read-ManualJson (Request-Manual ('/manual-review/state/' + $fixture.rejectId))
Assert-Manual ($state.ticket.status -eq 'REJECTED' -and $state.notifications.Count -eq 1 -and $null -eq $state.notifications[0].delivered_at) 'Reject retains pending email handoff'

$before = Read-ManualJson (Request-Manual ('/manual-review/state/' + $fixture.changesId))
$response = Request-Manual '/api/v1/reviews/request-changes' 'Post' @{ticketIds = @($fixture.changesId, $fixture.secondChangesId); comment = 'Add exact reproduction steps'}
Assert-Manual ($response.StatusCode -eq 200 -and (Read-ManualJson $response).count -eq 2) 'Batch Request changes returns count 2'
$after = Read-ManualJson (Request-Manual ('/manual-review/state/' + $fixture.changesId))
Assert-Manual ($after.ticket.status -eq 'CHANGES_REQUESTED' -and $before.ticket.review_due_at -eq $after.ticket.review_due_at -and $after.notifications.Count -eq 1) 'Request changes preserves deadline and queues email'
$second = Read-ManualJson (Request-Manual ('/manual-review/state/' + $fixture.secondChangesId))
Assert-Manual ($second.ticket.status -eq 'CHANGES_REQUESTED' -and $second.ticket.review_comment -eq 'Add exact reproduction steps' -and $second.decisions.Count -eq 1 -and $second.audit.Count -eq 1 -and $second.notifications.Count -eq 1) 'Second selected ticket gets the same comment and its own decision/audit/notification'

function testManualProblemBody_BUG_017 {
    $response = Request-Manual '/api/v1/reviews/approve' 'Post' @{ticketIds = @($fixture.foreignTicketId)}
    $problem = Read-ManualJson $response
    Assert-Manual ($response.StatusCode -eq 403 -and $problem.code -eq 'MSG08') 'BUG-017: decode Problem Details; forbidden Project returns MSG08'
}

testManualProblemBody_BUG_017
$response = Request-Manual '/api/v1/reviews/approve' 'Post' @{ticketIds = @($fixture.foreignTicketId)}
Assert-Manual ($response.StatusCode -eq 403) 'Foreign Project ticket returns 403'
$response = Request-Manual '/api/v1/reviews/approve' 'Post' @{ticketIds = @($fixture.rejectId)}
Assert-Manual ($response.StatusCode -eq 409) 'Rejected remains final'
$response = Request-Manual '/api/v1/reviews/approve' 'Post' @{ticketIds = @(1.5)}
Assert-Manual ($response.StatusCode -eq 400) 'BUG-011: fractional ticket ID rejected'
$response = Request-Manual '/api/v1/review-queue' 'Get' $null 'employee'
Assert-Manual ($response.StatusCode -eq 403) 'Employee cannot review'
$response = Request-Manual '/api/v1/review-queue?projectId=20' 'Get' $null 'otherpm'
Assert-Manual ($response.StatusCode -eq 200) 'Other PM can read own queue'

$batch = Read-ManualJson (Request-Manual '/manual-review/fixtures' 'Post')
$response = Request-Manual '/api/v1/reviews/approve' 'Post' @{ticketIds = @($batch.approveId, $batch.foreignTicketId)}
$state = Read-ManualJson (Request-Manual ('/manual-review/state/' + $batch.approveId))
Assert-Manual ($response.StatusCode -eq 403 -and $state.ticket.status -eq 'PENDING_REVIEW' -and $state.decisions.Count -eq 0) 'Mixed batch failure leaves valid ticket unchanged'

# Leave a fresh batch for the user's manual walkthrough; preserve existing history.
$fresh = Read-ManualJson (Request-Manual '/manual-review/fixtures' 'Post')
[pscustomobject]@{Passed = $checks.Count; Checks = $checks; Fixtures = $fresh} | ConvertTo-Json -Depth 5
