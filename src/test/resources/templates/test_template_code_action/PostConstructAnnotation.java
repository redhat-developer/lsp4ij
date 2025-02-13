package io.openliberty.sample.jakarta.annotations;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

@Resource(type = Object.class, name = "aa")
public class PostConstructAnnotation {

    private Integer studentId;

    private boolean isHappy;

    private boolean isSad;

    @PostConstruct()
    public Integer getStudentId() {
        return this.studentId;
    }

    @PostConstruct
    public void getHappiness(String type) {

    }

    @PostConstruct
    public void throwTantrum() throws Exception {
        System.out.println("I'm sad");
    }

    private String emailAddress;

}