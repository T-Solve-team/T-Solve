<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("logoutConfirmTitle")}
    <#elseif section = "form">
        <p class="ts-message">${msg("logoutConfirmHeader")}</p>
        <form action="${url.logoutConfirmAction}" method="POST">
            <input type="hidden" name="session_code" value="${logoutConfirm.code}">
            <input class="ts-button" name="confirmLogout" id="kc-logout" type="submit" value="${msg("doLogout")}"/>
        </form>
        <#if !logoutConfirm.skipLink>
            <@layout.backLink/>
        </#if>
    </#if>
</@layout.registrationLayout>
