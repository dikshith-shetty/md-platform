package com.rc.md.common.api;

import java.util.List;

public class HistoryResponse {

    private String s;
    private List<Long> t;
    private List<Double> o;
    private List<Double> h;
    private List<Double> l;
    private List<Double> c;
    private List<Long> v;
    private String message;

    public String getS() { return s; }
    public void setS(String s) { this.s = s; }
    public List<Long> getT() { return t; }
    public void setT(List<Long> t) { this.t = t; }
    public List<Double> getO() { return o; }
    public void setO(List<Double> o) { this.o = o; }
    public List<Double> getH() { return h; }
    public void setH(List<Double> h) { this.h = h; }
    public List<Double> getL() { return l; }
    public void setL(List<Double> l) { this.l = l; }
    public List<Double> getC() { return c; }
    public void setC(List<Double> c) { this.c = c; }
    public List<Long> getV() { return v; }
    public void setV(List<Long> v) { this.v = v; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
