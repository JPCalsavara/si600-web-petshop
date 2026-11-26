package br.unicamp.ft.si600.eventos.dto;

/** projectId só vem preenchido para o perfil CLIENT (o único projeto dele). */
public record LoginResponse(String token, String email, String role, String projectId) {}
