package com.nuno.kafkalab.authservice.responses;

// accessToken vai no header "Authorization: Bearer <accessToken>"; expiresIn em segundos
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
