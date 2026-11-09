<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex">
    <title>T-Solve</title>
    <link href="${url.resourcesPath}/css/tsolve.css" rel="stylesheet" />
</head>
<body>
<main class="ts-card">
    <svg class="ts-logo" viewBox="0 0 48 48" width="48" height="48" role="img" aria-label="T-Solve" xmlns="http://www.w3.org/2000/svg">
        <rect width="48" height="48" rx="10" fill="#1d4ed8"/>
        <path d="M12 14h24v6h-9v16h-6V20h-9z" fill="#ffffff"/>
    </svg>
    <h1 class="ts-title"><#nested "header"></h1>
    <#nested "form">
</main>
</body>
</html>
</#macro>

<#-- One "Back to sign-in" link: the client's base URL, or the theme property when Keycloak does not know the client. -->
<#macro backLink>
    <#assign backUrl = ((client.baseUrl)!"")?has_content?then(client.baseUrl, properties.backToSignInUrl!"")>
    <#if backUrl?has_content>
        <p><a class="ts-link" href="${backUrl}">${msg("backToApplication")}</a></p>
    </#if>
</#macro>
