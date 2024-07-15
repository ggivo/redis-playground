package com.redis.examples.consumer;


import com.fasterxml.jackson.annotation.JsonProperty;

public class Message {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("processed_by")
    private String processedBy;

    @JsonProperty("random_property")
    private String randomProperty;

    public Message(String messageId) {
        this.messageId = messageId;
    }

    public Message(String messageId, String processedBy, String randomProperty) {
        this.messageId = messageId;
        this.processedBy = processedBy;
        this.randomProperty = randomProperty;
    }


    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public String getRandomProperty() {
        return randomProperty;
    }

    public void setRandomProperty(String randomProperty) {
        this.randomProperty = randomProperty;
    }
}
