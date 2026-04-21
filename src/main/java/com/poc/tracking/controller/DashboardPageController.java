package com.poc.tracking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller para retornar página do dashboard da PoC.
 *
 * <p>Fornece endpoints para executar e comparar os resultados dos dois cenários
 * em uma única operação, facilitando a análise estatística descritiva.
 *
 * <p>Endpoints disponíveis:
 * <ul>
 *   <li>{@code GET /home} - Gera relatório/DashBoard comparativo de métricas.</li>
 * </ul>
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@Controller
public class DashboardPageController {

	@GetMapping("/home")
    public String openDashboard() {
        return "index"; // Retorna index.html
    }
}
