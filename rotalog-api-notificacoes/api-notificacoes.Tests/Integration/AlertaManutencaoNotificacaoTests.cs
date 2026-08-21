using System.Text;
using System.Text.Json;
using api_notificacoes.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace api_notificacoes.Tests.Integration;

/// <summary>
/// Teste de integração HTTP real (via WebApplicationFactory/TestServer) do contrato
/// consumido pelo AlertaNotificacaoClient de rotalog-api-frotas
/// (POST /api/notificacoes, tipo "ALERTA_MANUTENCAO").
///
/// Cobre apenas essa comunicação — não é um teste geral do endpoint POST /api/notificacoes.
///
/// Nota (fora do escopo deste teste, reportar para decisão futura): o payload real enviado
/// pelo AlertaNotificacaoClient não inclui "servicoOrigem" nem "referenciaId", diferente das
/// demais integrações registradas em rotalog-workspace/tools/scripts/07-seed-notificacoes.sql,
/// que sempre preenchem os dois campos. O payload abaixo reflete fielmente o que o cliente
/// Java envia hoje.
/// </summary>
public class AlertaManutencaoNotificacaoTests : IClassFixture<AlertaManutencaoWebApplicationFactory>
{
    private const string PayloadAlertaManutencao =
        "{" +
        "\"tipo\": \"ALERTA_MANUTENCAO\"," +
        "\"destinatario\": \"gestor@rotalog.com\"," +
        "\"mensagem\": \"Veiculo ABC-1234 esta proximo da manutencao preventiva (5000 km restantes).\"," +
        "\"canal\": \"email\"" +
        "}";

    private readonly AlertaManutencaoWebApplicationFactory _factory;

    public AlertaManutencaoNotificacaoTests(AlertaManutencaoWebApplicationFactory factory)
    {
        _factory = factory;
    }

    [Fact]
    public async Task PostNotificacoes_ComPayloadDeAlertaManutencaoDoApiFrotas_RetornaSucessoEPersisteNotificacao()
    {
        // Arrange
        var client = _factory.CreateClient();
        var conteudo = new StringContent(PayloadAlertaManutencao, Encoding.UTF8, "application/json");

        // Act
        var response = await client.PostAsync("/api/notificacoes", conteudo);
        var corpoResposta = await response.Content.ReadAsStringAsync();

        // Assert: chamada HTTP teve sucesso (2xx), exatamente como o AlertaNotificacaoClient espera
        // para não tratar o caso como "pendente" por erro de conexão.
        Assert.True(
            (int)response.StatusCode is >= 200 and < 300,
            $"Esperado HTTP 2xx, recebido {(int)response.StatusCode}. Corpo: {corpoResposta}");

        // Assert: corpo da resposta contém um campo "status" (camelCase) não vazio
        using var json = JsonDocument.Parse(corpoResposta);
        Assert.True(
            json.RootElement.TryGetProperty("status", out var statusProperty),
            $"Resposta não contém o campo 'status'. Corpo: {corpoResposta}");

        var status = statusProperty.GetString();
        Assert.False(string.IsNullOrEmpty(status), $"Campo 'status' veio vazio. Corpo: {corpoResposta}");

        // O EnviarEmail (fake) tem 10% de chance de falha simulada (débito técnico intencional,
        // não é flakiness do teste) - "ENVIADO" e "PENDENTE" são ambos resultados válidos de uma
        // chamada HTTP que teve sucesso.
        Assert.True(
            status is "ENVIADO" or "PENDENTE",
            $"Status inesperado para uma resposta 2xx: '{status}'. Corpo: {corpoResposta}");

        // Assert: a notificação foi persistida com Tipo/Canal corretos
        using var scope = _factory.Services.CreateScope();
        var context = scope.ServiceProvider.GetRequiredService<NotificacoesDbContext>();

        var notificacoesPersistidas = await context.Notificacoes
            .Where(n => n.Tipo == "ALERTA_MANUTENCAO" && n.Canal == "email")
            .ToListAsync();

        var notificacaoPersistida = Assert.Single(notificacoesPersistidas);
        Assert.Equal("gestor@rotalog.com", notificacaoPersistida.Destinatario);
    }
}
