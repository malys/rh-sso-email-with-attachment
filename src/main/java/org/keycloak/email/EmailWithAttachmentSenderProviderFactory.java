package org.keycloak.email;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class EmailWithAttachmentSenderProviderFactory implements EmailSenderProviderFactory {

    private String include;
    private Boolean parent;

    @Override
    public EmailSenderProvider create(KeycloakSession session) {
        return new EmailWithAttachmentSenderProvider(session, this.include, this.parent);
    }

    @Override
    public void init(Config.Scope config) {
        this.include = config.get("include");
        this.parent = Boolean.valueOf(config.get("parent"));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "emailwithattachment";
    }

}
