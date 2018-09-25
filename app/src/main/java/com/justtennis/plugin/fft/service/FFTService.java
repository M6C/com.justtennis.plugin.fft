package com.justtennis.plugin.fft.service;

import android.content.Context;

import com.justtennis.plugin.converter.LoginFormResponseConverter;
import com.justtennis.plugin.converter.PalmaresMillesimeFormResponseConverter;
import com.justtennis.plugin.fft.exception.NotConnectedException;
import com.justtennis.plugin.fft.model.FFTLoginFormRequest;
import com.justtennis.plugin.fft.model.FFTRankingListRequest;
import com.justtennis.plugin.fft.model.FFTRankingMatchRequest;
import com.justtennis.plugin.fft.model.LoginFormResponse;
import com.justtennis.plugin.fft.model.PalmaresMillesimeRequest;
import com.justtennis.plugin.fft.model.PalmaresMillesimeResponse;
import com.justtennis.plugin.fft.model.PalmaresRequest;
import com.justtennis.plugin.fft.model.PalmaresResponse;
import com.justtennis.plugin.fft.model.RankingListResponse;
import com.justtennis.plugin.fft.model.RankingMatchResponse;
import com.justtennis.plugin.fft.network.HttpGetProxy;
import com.justtennis.plugin.fft.network.HttpPostProxy;
import com.justtennis.plugin.fft.network.model.ResponseHttp;
import com.justtennis.plugin.fft.network.tool.NetworkTool;
import com.justtennis.plugin.fft.parser.FormParser;
import com.justtennis.plugin.fft.parser.PalmaresParser;
import com.justtennis.plugin.fft.parser.RankingParser;
import com.justtennis.plugin.fft.preference.FFTSharedPref;
import com.justtennis.plugin.fft.preference.ProxySharedPref;
import com.justtennis.plugin.fft.skeleton.IProxy;

import org.jsoup.helper.StringUtil;

import java.io.IOException;
import java.util.Map;

public class FFTService implements IProxy {

    private static final String URL_ROOT = "https://mon-espace-tennis.fft.fr";
    private static final String LOGON_SITE = "mon-espace-tennis.fft.fr";
    private static final int    LOGON_PORT = 80;
    private static final String LOGON_METHOD = "https";

    private Context context;
    private String proxyHost;
    private int    proxyPort;
    private String proxyUser;
    private String proxyPw;

    private FFTService(Context context) {
        this.context = context;
    }

    public static FFTService newInstance(Context context) {
        FFTService service = new FFTService(context);
        if (ProxySharedPref.getUseProxy(context)) {
            service.setProxyHost(ProxySharedPref.getSite(context))
                    .setProxyPort(ProxySharedPref.getPort(context))
                    .setProxyUser(ProxySharedPref.getUser(context))
                    .setProxyPw(ProxySharedPref.getPwd(context));
        }
        return service;
    }

    public LoginFormResponse getLoginForm(String login, String password) {
        logMethod("getLoginForm");
        LoginFormResponse ret = null;
        System.out.println("\r\n" + URL_ROOT);

        ResponseHttp respRoot = doGet(URL_ROOT, "");
        System.out.println("==============> connection Return:\r\n" + respRoot.body);

        if (!StringUtil.isBlank(respRoot.body)) {
            ret = FormParser.parseFormLogin(respRoot.body, new FFTLoginFormRequest());
            ret.login.value = login;
            ret.password.value = password;
        }

        return ret;
    }

    public ResponseHttp submitFormLogin(LoginFormResponse form) {
        logMethod("submitFormLogin");
        ResponseHttp ret = null;

        System.out.println("");
        System.out.println("==============> Form Action:" + form.action);

        Map<String, String> data = LoginFormResponseConverter.toDataMap(form);
        if (!StringUtil.isBlank(form.action)) {
            ret = doPost(URL_ROOT, form.action, data);

            String cookie = NetworkTool.buildCookie(ret);
            if (!cookie.isEmpty()) {
                FFTSharedPref.setCookie(context, cookie);
                FFTSharedPref.setHomePage(context, form.action);
            } else {
                FFTSharedPref.clean(context);
            }
        } else {
            FFTSharedPref.clean(context);
        }

        return ret;
    }

    public ResponseHttp navigateToFormRedirect(ResponseHttp loginFormResponse) throws NotConnectedException {
        logMethod("navigateToFormRedirect");
        if (loginFormResponse.pathRedirect != null && !loginFormResponse.pathRedirect.isEmpty()) {
            ResponseHttp responseHttp = doGetConnected(URL_ROOT, loginFormResponse.pathRedirect, loginFormResponse);
            if (NetworkTool.isOk(responseHttp.statusCode)) {
                FFTSharedPref.setHomePage(context, responseHttp.pathRedirect);
            } else {
                FFTSharedPref.clean(context);
            }
            return responseHttp;
        }
        return null;
    }

    public ResponseHttp navigateToHomePage(ResponseHttp loginFormResponse) throws NotConnectedException {
        logMethod("navigateToHomePage");
        String homePage = FFTSharedPref.getHomePage(context);
        if (homePage != null && !homePage.isEmpty()) {
            return doGetConnected(URL_ROOT, homePage, loginFormResponse);
        } else {
            throw new NotConnectedException("navigateToHomePage - No Home Page found");
        }
    }

    public ResponseHttp navigateToRanking(ResponseHttp loginFormResponse) throws NotConnectedException {
        logMethod("navigateToRanking");
        return doGetConnected(URL_ROOT, "/bloc_home/redirect/classement", loginFormResponse);
    }

    public RankingListResponse getRankingList(ResponseHttp loginFormResponse) throws NotConnectedException {
        logMethod("getRankingList");
        ResponseHttp respRoot = doGetConnected(URL_ROOT, "/bloc_home/redirect/classement", loginFormResponse);
        System.out.println("==============> connection Return:\r\n" + respRoot.body);

        return RankingParser.parseRankingList(respRoot.body, new FFTRankingListRequest());
    }

    public RankingMatchResponse getRankingMatch(ResponseHttp loginFormResponse, String id) throws NotConnectedException {
        logMethod("getRankingMatch");
        ResponseHttp respRoot = doGetConnected(URL_ROOT, "/page_classement_ajax?id_bilan=" + id, loginFormResponse);
        if (!StringUtil.isBlank(respRoot.body)) {
            respRoot.body = format(respRoot.body);
            System.out.println("==============> getRankingMatch formated ranking.body:" + respRoot.body);
        }
        return RankingParser.parseRankingMatch(respRoot.body, new FFTRankingMatchRequest());
    }

    public PalmaresResponse getPalmares(ResponseHttp loginFormResponse) {
        logMethod("getPalmares");
        System.out.println("==============> body:" + loginFormResponse.body);
        return PalmaresParser.parsePalmares(loginFormResponse.body, new PalmaresRequest());
    }

    public ResponseHttp navigateToPalmares(ResponseHttp loginFormResponse, PalmaresResponse palmaresResponse) throws NotConnectedException {
        logMethod("navigateToPalmares");
        return doGetConnected(URL_ROOT, palmaresResponse.action, loginFormResponse);
    }

    public PalmaresMillesimeResponse getPalmaresMillesime(ResponseHttp palamresResponseHttp) {
        logMethod("getPalmaresMillesime");
        System.out.println("==============> body:" + palamresResponseHttp.body);
        return PalmaresParser.parsePalmaresMillesime(palamresResponseHttp.body, new PalmaresMillesimeRequest());
    }

    public ResponseHttp submitFormPalmaresMillesime(ResponseHttp loginFormResponse, PalmaresMillesimeResponse form) throws IOException, NotConnectedException {
        logMethod("submitFormLogin");
        ResponseHttp ret = null;

        System.out.println("");
        System.out.println("==============> Form Action:" + form.action);

        form.select.value = form.millesimeSelected.value;

        Map<String, String> data = PalmaresMillesimeFormResponseConverter.toDataMap(form);
        if (!StringUtil.isBlank(form.action)) {
            ret = doPostConnected(URL_ROOT, form.action, data, loginFormResponse);
        }

        return ret;
    }

    private String format(String str) {
        String ret = decode(str);
        ret = ret.replaceAll("\\\\n", "");
        ret = ret.replaceAll("\\\\/", "/");
        return ret;
    }

    private String decode(final String in) {
        String working = in;
        int index;
        index = working.indexOf("\\u");
        while(index > -1) {
            int length = working.length();
            if(index > (length-6))break;
            int numStart = index + 2;
            int numFinish = numStart + 4;
            String substring = working.substring(numStart, numFinish);
            int number = Integer.parseInt(substring,16);
            String stringStart = working.substring(0, index);
            String stringEnd   = working.substring(numFinish);
            working = stringStart + ((char)number) + stringEnd;
            index = working.indexOf("\\u");
        }
        return working;
    }

    private HttpGetProxy newHttpGetProxy() {
        HttpGetProxy instance = HttpGetProxy.newInstance();
        setProxy(instance);
        return instance;
    }

    private ResponseHttp doGet(String root, String path) {
        return newHttpGetProxy().get(root, path);
    }

    private ResponseHttp doPost(String root, String path, Map<String, String> data) {
        return newHttpPostProxy().post(root, path, data);
    }

    private ResponseHttp doGetConnected(String root, String path, ResponseHttp http) throws NotConnectedException {
        String cookie = FFTSharedPref.getCookie(context);
        if (cookie == null && http == null) {
            throw new NotConnectedException();
        } else if (cookie != null) {
            return newHttpGetProxy().get(root, path, cookie);
        } else {
            return newHttpGetProxy().get(root, path, http);
        }
    }

    private ResponseHttp doPostConnected(String root, String path, Map<String, String> data, ResponseHttp http) throws NotConnectedException {
        String cookie = FFTSharedPref.getCookie(context);
        if (cookie == null && http == null) {
            throw new NotConnectedException();
        } else if (cookie != null) {
            return newHttpPostProxy().post(root, path, data, cookie);
        } else {
            return newHttpPostProxy().post(root, path, data, http);
        }
    }

    private HttpPostProxy newHttpPostProxy() {
        HttpPostProxy instance = HttpPostProxy.newInstance();
        setProxy(instance);
        instance.setSite(LOGON_SITE)
                .setPort(LOGON_PORT)
                .setMethod(LOGON_METHOD);
        return instance;
    }

    private void setProxy(IProxy instance) {
        instance.setProxyHost(proxyHost)
                .setProxyPort(proxyPort)
                .setProxyUser(proxyUser)
                .setProxyPw(proxyPw);
    }

    @Override
    public IProxy setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    @Override
    public IProxy setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    @Override
    public IProxy setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
        return this;
    }

    @Override
    public IProxy setProxyPw(String proxyPw) {
        this.proxyPw = proxyPw;
        return this;
    }

    private void logMethod(String method) {
        System.out.println("\n==========================================================================");
        System.out.println("==============> Method:" + method);
        System.out.println("==========================================================================\r\n");

    }
}
