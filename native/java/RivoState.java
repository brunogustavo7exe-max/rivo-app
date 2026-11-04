package com.dcastrolabs.rivo;

/** Guarda se o app está na frente da tela (aí quem toca o alerta é o próprio app, não o serviço). */
public class RivoState {
    static volatile boolean fg = false;
}
