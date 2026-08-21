using api_notificacoes.Data;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace api_notificacoes.Tests.Integration;

/// <summary>
/// Factory de testes de integração para o contrato consumido pelo AlertaNotificacaoClient
/// (rotalog-api-frotas).
///
/// Não há Postgres acessível no ambiente de teste/CI, então o registro padrão de
/// NotificacoesDbContext (UseNpgsql, feito em Program.cs) é substituído aqui por
/// Microsoft.EntityFrameworkCore.InMemory, mantendo o teste determinístico e sem
/// dependência de infraestrutura externa.
/// </summary>
public class AlertaManutencaoWebApplicationFactory : WebApplicationFactory<Program>
{
    // Nome único por instância da factory, para isolar o banco em memória entre execuções
    // e ainda permitir consultar, depois do POST, o mesmo contexto usado pela aplicação.
    private readonly string _databaseName = $"notificacoes-tests-{Guid.NewGuid()}";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureServices(services =>
        {
            services.RemoveAll<DbContextOptions<NotificacoesDbContext>>();

            services.AddDbContext<NotificacoesDbContext>(options =>
                options.UseInMemoryDatabase(_databaseName));
        });
    }
}
