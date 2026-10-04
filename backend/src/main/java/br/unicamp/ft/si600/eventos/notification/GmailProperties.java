package br.unicamp.ft.si600.eventos.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais OAuth2 da conta Gmail remetente. O escopo necessário é
 * https://www.googleapis.com/auth/gmail.send.
 */
@ConfigurationProperties(prefix = "notification.gmail")
public class GmailProperties {
    private boolean enabled = false;
    private String sender;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String apiBaseUrl = "https://gmail.googleapis.com";
    private String tokenUrl = "https://oauth2.googleapis.com/token";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
}
