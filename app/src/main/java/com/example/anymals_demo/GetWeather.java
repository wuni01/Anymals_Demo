package com.example.anymals_demo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GetWeather {
    @SerializedName("response")
    public Response response;

    public static class Response {
        @SerializedName("body")
        public Body body;
    }

    public static class Body {
        @SerializedName("items")
        public Items items;
    }

    public static class Items {
        @SerializedName("item")
        public List<WeatherItem> item;
    }

    public static class WeatherItem {
        @SerializedName("category")
        public String category; // T1H(기온), PTY(강수형태) 등
        @SerializedName("fcstValue")
        public String fcstValue; // 값
        @SerializedName("fcstDate")   // 기상청 데이터에는 날짜도 오므로 추가 권장
        public String fcstDate;
        @SerializedName("fcstTime")   // 시간도 추가 권장
        public String fcstTime;
        @SerializedName("obsrValue")
        public String obsrValue;
    }
}