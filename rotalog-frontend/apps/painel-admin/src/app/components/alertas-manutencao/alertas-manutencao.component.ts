import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FrotasService } from '../../services/frotas.service';
import { AlertaManutencao } from '../../models';

// Interface local só com os campos que a tela usa: a interface `Veiculo` compartilhada
// (models/index.ts) declara nomes de campo que não batem com o JSON real da API.
interface VeiculoResumo {
  id: number;
  placa: string;
  modelo: string;
  quilometragem: number;
}

interface BadgeInfo {
  label: string;
  className: string;
}

interface AlertaComVeiculo {
  alerta: AlertaManutencao;
  veiculo: VeiculoResumo | undefined;
  motivo: BadgeInfo;
  statusNotificacao: BadgeInfo;
}

const MOTIVO_MAP: Record<string, BadgeInfo> = {
  KM_EXCEDIDO: { label: 'QUILOMETRAGEM_EXCEDIDA', className: 'motivo-KM_EXCEDIDO' },
  TEMPO_EXCEDIDO: { label: 'PRAZO_EXCEDIDO', className: 'motivo-TEMPO_EXCEDIDO' },
  KM_E_TEMPO_EXCEDIDOS: { label: 'QUILOMETRAGEM_E_PRAZO_EXCEDIDOS', className: 'motivo-KM_E_TEMPO_EXCEDIDOS' }
};

const STATUS_MAP: Record<string, BadgeInfo> = {
  ENVIADA: { label: 'ENVIADO', className: 'status-ENVIADA' },
  PENDENTE: { label: 'PENDENTE', className: 'status-PENDENTE' },
  FALHA: { label: 'FALHA', className: 'status-FALHA' }
};

@Component({
  selector: 'app-alertas-manutencao',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="alertas-page">
      <div class="page-header">
        <h1>Alertas de Manutenção</h1>

        <div class="filters">
          <select [(ngModel)]="filtroStatus" (change)="filtrar()">
            <option value="">Todos os status</option>
            <option value="PENDENTE">Pendente</option>
            <option value="ENVIADA">Enviada</option>
            <option value="FALHA">Falha</option>
          </select>
        </div>
      </div>

      <div *ngIf="loading" class="loading">Carregando alertas...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr>
            <th>ID</th>
            <th>Placa</th>
            <th>Modelo</th>
            <th>Km Atual</th>
            <th>Motivo</th>
            <th>Status Notificação</th>
            <th>Erro</th>
            <th>Data Criação</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of alertasView">
            <td>{{ item.alerta.id }}</td>
            <td>{{ item.veiculo?.placa || '—' }}</td>
            <td>{{ item.veiculo?.modelo || '—' }}</td>
            <td>{{ item.veiculo ? (item.veiculo.quilometragem | number) : '—' }}</td>
            <td>
              <span class="status-badge" [ngClass]="item.motivo.className">
                {{ item.motivo.label }}
              </span>
            </td>
            <td>
              <span class="status-badge" [ngClass]="item.statusNotificacao.className">
                {{ item.statusNotificacao.label }}
              </span>
            </td>
            <td>{{ item.alerta.mensagemErro || '—' }}</td>
            <td>{{ item.alerta.dataCriacao | date:'dd/MM/yyyy HH:mm' }}</td>
          </tr>
        </tbody>
      </table>

      <div *ngIf="!loading && alertasView.length === 0" class="empty-state">
        Nenhum alerta encontrado.
      </div>
    </div>
  `,
  styles: [`
    .alertas-page { padding: 20px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .page-header h1 { margin: 0; color: #333; font-size: 24px; }
    .filters { display: flex; gap: 12px; }
    .filters select { padding: 10px 14px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; }
    .loading { text-align: center; padding: 40px; color: #666; }
    .data-table { width: 100%; border-collapse: collapse; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
    .data-table th { text-align: left; padding: 14px; background: #f5f5f5; color: #666; font-size: 13px; text-transform: uppercase; }
    .data-table td { padding: 14px; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
    .data-table tr:hover { background: #f9f9f9; }
    .status-badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-PENDENTE { background: #fff3e0; color: #e65100; }
    .status-ENVIADA { background: #e8f5e9; color: #2e7d32; }
    .status-FALHA { background: #ffebee; color: #c62828; }
    .motivo-KM_EXCEDIDO { background: #e3f2fd; color: #1565c0; }
    .motivo-TEMPO_EXCEDIDO { background: #fce4ec; color: #c2185b; }
    .motivo-KM_E_TEMPO_EXCEDIDOS { background: #f3e5f5; color: #6a1b9a; }
    .motivo-outro { background: #eeeeee; color: #616161; }
    .empty-state { text-align: center; padding: 40px; color: #999; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
  `]
})
export class AlertasManutencaoComponent implements OnInit {
  alertasView: AlertaComVeiculo[] = [];
  loading = true;
  filtroStatus = '';

  constructor(private frotasService: FrotasService) {}

  ngOnInit(): void {
    this.carregarAlertas();
  }

  async carregarAlertas(): Promise<void> {
    this.loading = true;

    const [alertas, veiculos] = await Promise.all([
      this.frotasService.getAlertasManutencao(this.filtroStatus || undefined),
      // Cast necessário: a interface `Veiculo` compartilhada não reflete os campos reais da API.
      this.frotasService.getVeiculos() as unknown as Promise<VeiculoResumo[]>
    ]);

    const veiculosPorId = new Map(veiculos.map(veiculo => [veiculo.id, veiculo]));

    this.alertasView = alertas.map(alerta => ({
      alerta,
      veiculo: veiculosPorId.get(alerta.veiculoId),
      motivo: MOTIVO_MAP[alerta.motivo] || { label: alerta.motivo, className: 'motivo-outro' },
      statusNotificacao: STATUS_MAP[alerta.status] || { label: alerta.status, className: 'motivo-outro' }
    }));

    this.loading = false;
  }

  filtrar(): void {
    this.carregarAlertas();
  }
}
