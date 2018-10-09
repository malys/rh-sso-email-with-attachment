package org.keycloak.email;

import com.sun.mail.smtp.SMTPMessage;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeProvider;
import org.keycloak.truststore.HostnameVerificationPolicy;
import org.keycloak.truststore.JSSETruststoreConfigurator;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

@JBossLog
public class EmailWithAttachmentSenderProvider implements EmailSenderProvider {
    private final KeycloakSession session;

    private String include;
    private Boolean parent;

    public EmailWithAttachmentSenderProvider(KeycloakSession session, String include, Boolean parent) {
        this.session = session;
        this.include = include;
        this.parent = parent;
    }

    @Override
    public void send(Map<String, String> config, UserModel user, String subject, String textBody, String htmlBody) throws EmailException {
        Transport transport = null;
        try {
            String address = retrieveEmailAddress(user);

            Properties props = new Properties();

            if (config.containsKey("host")) {
                props.setProperty("mail.smtp.host", config.get("host"));
            }

            boolean auth = "true".equals(config.get("auth"));
            boolean ssl = "true".equals(config.get("ssl"));
            boolean starttls = "true".equals(config.get("starttls"));

            if (config.containsKey("port") && config.get("port") != null) {
                props.setProperty("mail.smtp.port", config.get("port"));
            }

            if (auth) {
                props.setProperty("mail.smtp.auth", "true");
            }

            if (ssl) {
                props.setProperty("mail.smtp.ssl.enable", "true");
            }

            if (starttls) {
                props.setProperty("mail.smtp.starttls.enable", "true");
            }

            if (ssl || starttls) {
                setupTruststore(props);
            }

            props.setProperty("mail.smtp.timeout", "10000");
            props.setProperty("mail.smtp.connectiontimeout", "10000");

            String from = config.get("from");
            String fromDisplayName = config.get("fromDisplayName");
            String replyTo = config.get("replyTo");
            String replyToDisplayName = config.get("replyToDisplayName");
            String envelopeFrom = config.get("envelopeFrom");

            Session session = Session.getInstance(props);

            Multipart multipart = new MimeMultipart("alternative");

            if (textBody != null) {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(textBody, "UTF-8");
                multipart.addBodyPart(textPart);
            }

            if (htmlBody != null) {
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
                multipart.addBodyPart(htmlPart);
                addResources(multipart, config);
            }

            SMTPMessage msg = new SMTPMessage(session);
            msg.setFrom(toInternetAddress(from, fromDisplayName));

            msg.setReplyTo(new Address[]{toInternetAddress(from, fromDisplayName)});
            if (replyTo != null && !replyTo.isEmpty()) {
                msg.setReplyTo(new Address[]{toInternetAddress(replyTo, replyToDisplayName)});
            }
            if (envelopeFrom != null && !envelopeFrom.isEmpty()) {
                msg.setEnvelopeFrom(envelopeFrom);
            }

            msg.setHeader("To", address);
            msg.setSubject(subject, "utf-8");
            msg.setContent(multipart);
            msg.saveChanges();
            msg.setSentDate(new Date());

            transport = session.getTransport("smtp");
            if (auth) {
                transport.connect(config.get("user"), config.get("password"));
            } else {
                transport.connect();
            }
            transport.sendMessage(msg, new InternetAddress[]{new InternetAddress(address)});
        } catch (Exception e) {
            ServicesLogger.LOGGER.failedToSendEmail(e);
            throw new EmailException(e);
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException e) {
                    log.warn("Failed to close transport", e);
                }
            }
        }
    }

    protected InternetAddress toInternetAddress(String email, String displayName) throws UnsupportedEncodingException, AddressException, EmailException {
        if (email == null || "".equals(email.trim())) {
            throw new EmailException("Please provide a valid address", null);
        }
        if (displayName == null || "".equals(displayName.trim())) {
            return new InternetAddress(email);
        }
        return new InternetAddress(email, displayName, "utf-8");
    }

    protected String retrieveEmailAddress(UserModel user) {
        return user.getEmail();
    }

    private void setupTruststore(Properties props) throws NoSuchAlgorithmException, KeyManagementException {

        JSSETruststoreConfigurator configurator = new JSSETruststoreConfigurator(session);

        SSLSocketFactory factory = configurator.getSSLSocketFactory();
        if (factory != null) {
            props.put("mail.smtp.ssl.socketFactory", factory);
            if (configurator.getProvider().getPolicy() == HostnameVerificationPolicy.ANY) {
                props.setProperty("mail.smtp.ssl.trust", "*");
            }
        }
    }

    @Override
    public void close() {

    }

    private void addAttachment(Multipart multipart, String path) {
        MimeBodyPart htmlPart = new MimeBodyPart();
        DataSource source = new FileDataSource(path);
        try {
            htmlPart.setDataHandler(new DataHandler(source));
            htmlPart.setFileName(new File(path).toPath().getFileName().toString());
            multipart.addBodyPart(htmlPart);
        } catch (MessagingException e) {
            log.warn("Failed to attach file: " + path, e);
        }
    }

    private void addResources(Multipart multipart, Map<String, String> config) {
        if (include != null) {
            try {
                String imagesPath = getResourcePath(include);
                if (parent) {
                    Files.list(new File(imagesPath).getParentFile().toPath()).forEach((i) -> addAttachment(multipart, i.toString())
                    );
                } else {
                    addAttachment(multipart, imagesPath);
                }
            } catch (IOException me) {
                log.warn("Failed to attach files", me);
            }

        }
    }

    private String getResourcePath(String resPath) throws IOException {
        /*
         * We have to locate the resource path to embed images in the email.
         * Resource path can be obtain from the theme.
         */
        String emailTheme = session.getContext().getRealm().getEmailTheme();
        ThemeProvider themeProvider = session.getProvider(ThemeProvider.class, "extending");
        Theme theme = themeProvider.getTheme(emailTheme, Theme.Type.EMAIL);
        URL relativePath = theme.getResource(resPath);
        if (relativePath == null) throw new IOException("");
        return relativePath.getPath();
    }


}
