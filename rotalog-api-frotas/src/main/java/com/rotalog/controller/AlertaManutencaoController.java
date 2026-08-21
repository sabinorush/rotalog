package com.rotalog.controller;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.service.AlertaManutencaoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AlertaManutencaoController - REST controller para alertas de manutenção preventiva
 *
 * FIXME: Mesmo padrão copy-paste de tratamento de erro dos outros controllers
 * FIXME: Sem @ControllerAdvice
 */
@Slf4j
@RestController
@RequestMapping("/alertas-manutencao")
public class AlertaManutencaoController {

    @Autowired
    private AlertaManutencaoService alertaManutencaoService;

    @Autowired
    private AlertaManutencaoRepository alertaManutencaoRepository;

    @PostMapping("/verificar")
    public ResponseEntity<?> verificar() {
        try {
            List<AlertaManutencao> alertas = alertaManutencaoService.verificarEEmitirAlertas();
            return ResponseEntity.ok(alertas);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) StatusAlerta status) {
        try {
            if (status != null) {
                return ResponseEntity.ok(alertaManutencaoRepository.findByStatus(status));
            }
            return ResponseEntity.ok(alertaManutencaoRepository.findAll());
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}
