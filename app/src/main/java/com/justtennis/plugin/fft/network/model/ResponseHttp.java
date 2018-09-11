package com.justtennis.plugin.fft.network.model;

import java.util.ArrayList;
import java.util.List;

public class ResponseHttp {

    public int statusCode;
    public List<ResponseElement> header = new ArrayList<>();
    public String pathRedirect;
    public String body;
//    public String action;
//    public Map<String, String> input = new HashMap<>();
//    public ResponseElement login = new ResponseElement();
//    public ResponseElement password = new ResponseElement();
//    public ResponseElement button = new ResponseElement();


    @Override
    public String toString() {
        return "ResponseHttp{" +
                "statusCode=" + statusCode +
                ", header=" + header +
                ", pathRedirect='" + pathRedirect + '\'' +
                ", body='" + body + '\'' +
                '}';
    }
}