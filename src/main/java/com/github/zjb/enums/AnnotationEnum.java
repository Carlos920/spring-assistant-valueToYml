package com.github.zjb.enums;

import java.util.ArrayList;
import java.util.List;

public enum AnnotationEnum {
    FEIGN_CLIENT("FeignClient", "org.springframework.cloud.openfeign.FeignClient"),
    KAFKA_LISTENER("FeignClient", "org.springframework.kafka.annotation.KafkaListener"),
    ROCKETMQ_MESSAGE_LISTENER("RocketMQMessageListener", "org.apache.rocketmq.spring.annotation.RocketMQMessageListener"),
    VALUE("Value", "org.springframework.beans.factory.annotation.Value"),
    SCHEDULED("Scheduled", "org.springframework.scheduling.annotation.Scheduled"),
    /**
     * 注解<code>@ConfigurationProperties</code>，未包裹在<code>${}</code>中
     */
    CONFIGURATIONPROPERTIES("ConfigurationProperties", "org.springframework.boot.context.properties.ConfigurationProperties");

    private String name;
    private String qualifiedName;


    AnnotationEnum(String name, String qualifiedName) {
        this.name = name;
        this.qualifiedName = qualifiedName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * 无<code>${}</code>包裹
     *
     * @return []
     */
    public static List<String> special() {
        return new ArrayList<>() {{
            add(AnnotationEnum.CONFIGURATIONPROPERTIES.getQualifiedName());
        }};
    }
}