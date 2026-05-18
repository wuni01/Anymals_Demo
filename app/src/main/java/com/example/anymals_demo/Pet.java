package com.example.anymals_demo;

import java.time.LocalDate;
public class Pet {
    public String name;
    public String gender;
    public String breed;
    public String birthday;
    public double weight;
    public String age;
    public String profileImageUrl;

    public Pet() {}

    public Pet(String name, String gender, String breed, String birthday, double weight, String age) {
        this.name = name;
        this.gender = gender;
        this.breed = breed;
        this.birthday = birthday;
        this.weight = weight;
        this.age = age;
    }
    protected static String get_Age(String birthday) {
        LocalDate now = LocalDate.now();

        try {
            String [] birth = birthday.trim().split("-");
            int birth_year = Integer.parseInt(birth[0]);
            int current_year = now.getYear();

            if(birth_year == current_year) {
                int birth_month = Integer.parseInt(birth[1]);
                int current_month = now.getMonthValue();

                return Math.max(1, (current_month - birth_month)) + "개월";
            }
            return (current_year - birth_year) + "살";

        }
        catch(Exception e) {
            return "";
        }
    }

    protected static String get_Birthday(String birthday) {
        try {
            String [] birth = birthday.trim().split("-");

            return birth[0] + "년 " + birth[1] + "월 " + birth[2] + "일";
        }
        catch(Exception e) {
            return "";
        }
    }

}
