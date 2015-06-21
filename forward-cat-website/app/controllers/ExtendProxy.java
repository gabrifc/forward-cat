package controllers;

import com.forwardcat.common.ProxyMail;
import com.google.inject.Inject;
import models.ProxyRepository;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.i18n.Lang;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.proxy_extended;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import static models.ControllerUtils.*;
import static models.ExpirationUtils.*;

public class ExtendProxy extends Controller {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendProxy.class.getName());
    private final ProxyRepository proxyRepo;

    @Inject
    ExtendProxy(ProxyRepository proxyRepo) {
        this.proxyRepo = proxyRepo;
    }

    public Result extend(String p, String h) {
        Http.Request request = request();

        // Checking params
        Optional<MailAddress> maybeProxyMail = toMailAddress(p);
        if (!maybeProxyMail.isPresent() || h == null) {
            return badRequest();
        }

        // Getting the proxy
        MailAddress proxyMail = maybeProxyMail.get();
        Optional<ProxyMail> maybeProxy = proxyRepo.getProxy(proxyMail);
        if (!maybeProxy.isPresent()) {
            return badRequest();
        }

        // Checking that the hash is correct
        ProxyMail proxy = maybeProxy.get();
        String hashValue = getHash(proxy);
        if (!h.equals(hashValue)) {
            LOGGER.debug("Hash values are not equals {} - {}", h, hashValue);
            return badRequest();
        }

        // Checking that the proxy is active
        if (!proxy.isActive()) {
            LOGGER.debug("Proxy {} is already active", proxy);
            return badRequest();
        }

        // Checking that the proxy has not been active for more than 15 days

        ZonedDateTime maxExpirationTime = toZonedDateTime(proxy.getCreationTime()).plusDays(getMaxProxyDuration());
        ZonedDateTime newExpirationTime = getNewExpirationTime(toZonedDateTime(proxy.getExpirationTime()).plusDays(getIncrementDaysAdded()), maxExpirationTime);

        Date expirationTimeDate = toDate(newExpirationTime);
        proxy.setExpirationTime(expirationTimeDate);

        proxyRepo.update(proxy);

        // Generating the answer
        Lang language = getBestLanguage(request, lang());
        String date = formatInstant(expirationTimeDate, language);
        return ok(proxy_extended.render(language, proxyMail.toString(), date));
    }

    private ZonedDateTime getNewExpirationTime(ZonedDateTime newExpirationTime, ZonedDateTime maxExpirationTime) {
        if (newExpirationTime.isAfter(maxExpirationTime)) {
            return maxExpirationTime;
        }
        return newExpirationTime;
    }
}
