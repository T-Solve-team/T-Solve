<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("pageExpiredTitle")}
    <#elseif section = "form">
        <p class="ts-message">${msg("somethingWrong")}</p>
        <@layout.backLink/>
    </#if>
</@layout.registrationLayout>
