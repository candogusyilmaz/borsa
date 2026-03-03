import type { ReactElement } from 'react';
import {
  Area,
  AreaChart as RechartsAreaChart,
  Bar,
  BarChart as RechartsBarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';

type ChartRecord = Record<string, number | string | null | undefined>;

type SeriesItem = { name: string; color?: string; label?: string; type?: 'bar' | 'area' | 'line' };

type SharedProps = {
  data?: ChartRecord[];
  dataKey?: string;
  h?: number | string;
  valueFormatter?: (value: number) => string;
};

function Wrapper({ height = 300, children }: { height?: number | string; children: ReactElement }) {
  return (
    <div style={{ width: '100%', height: typeof height === 'number' ? `${height}px` : height }}>
      <ResponsiveContainer>{children}</ResponsiveContainer>
    </div>
  );
}

export function AreaChart({ data = [], dataKey = 'name', series = [], h, valueFormatter }: SharedProps & { series?: SeriesItem[]; [key: string]: unknown }) {
  return (
    <Wrapper height={h ?? 280}>
      <RechartsAreaChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
        <XAxis dataKey={dataKey} />
        <YAxis tickFormatter={valueFormatter} />
        <Tooltip formatter={valueFormatter} />
        {series.map((entry) => (
          <Area key={entry.name} type="monotone" dataKey={entry.name} stroke={entry.color ?? '#6366f1'} fill={entry.color ?? '#6366f1'} fillOpacity={0.2} />
        ))}
      </RechartsAreaChart>
    </Wrapper>
  );
}

export function BarChart({ data = [], dataKey = 'name', series = [], h, valueFormatter, getBarColor }: SharedProps & { series?: SeriesItem[]; getBarColor?: (value: number) => string; [key: string]: unknown }) {
  return (
    <Wrapper height={h ?? 280}>
      <RechartsBarChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
        <XAxis dataKey={dataKey} />
        <YAxis tickFormatter={valueFormatter} />
        <Tooltip formatter={valueFormatter} />
        {series.map((entry) => (
          <Bar key={entry.name} dataKey={entry.name} fill={entry.color ?? '#6366f1'}>
            {getBarColor
              ? data.map((item, index) => (
                  <Cell key={`${entry.name}-${index}`} fill={getBarColor(Number(item[entry.name] ?? 0))} />
                ))
              : null}
          </Bar>
        ))}
      </RechartsBarChart>
    </Wrapper>
  );
}

export function CompositeChart({
  data = [],
  dataKey = 'name',
  series = [],
  h,
  valueFormatter,
  xAxisProps,
  yAxisProps,
  strokeWidth = 2
}: SharedProps & {
  series?: SeriesItem[];
  xAxisProps?: { tickFormatter?: (value: string) => string; [key: string]: unknown };
  yAxisProps?: { tickFormatter?: (value: number) => string };
  strokeWidth?: number;
  [key: string]: unknown;
}) {
  return (
    <Wrapper height={h ?? 280}>
      <ComposedChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
        <XAxis dataKey={dataKey} tickFormatter={xAxisProps?.tickFormatter} />
        <YAxis tickFormatter={yAxisProps?.tickFormatter} />
        <Tooltip formatter={valueFormatter} />
        {series.map((entry) => {
          if (entry.type === 'area') {
            return <Area key={entry.name} dataKey={entry.name} stroke={entry.color ?? '#6366f1'} fill={entry.color ?? '#6366f1'} fillOpacity={0.2} strokeWidth={strokeWidth} />;
          }
          if (entry.type === 'bar') {
            return <Bar key={entry.name} dataKey={entry.name} fill={entry.color ?? '#64748b'} />;
          }
          return <Line key={entry.name} dataKey={entry.name} stroke={entry.color ?? '#6366f1'} strokeWidth={strokeWidth} />;
        })}
      </ComposedChart>
    </Wrapper>
  );
}

type DonutDatum = { name: string; value: number; color?: string };

export function DonutChart({
  data = [],
  size = 200,
  thickness = 20,
  pieProps,
  paddingAngle = 0
}: {
  data?: DonutDatum[];
  size?: number;
  thickness?: number;
  pieProps?: { onMouseEnter?: (item: { name: string; value: number; percent: number }) => void; onMouseLeave?: () => void; cornerRadius?: number; paddingAngle?: number; [key: string]: unknown };
  paddingAngle?: number;
  [key: string]: unknown;
}) {
  const total = data.reduce((acc, entry) => acc + entry.value, 0);
  const normalized = data.map((entry) => ({ ...entry, percent: total ? entry.value / total : 0 }));

  return (
    <div style={{ width: size, height: size }}>
      <ResponsiveContainer>
        <PieChart>
          <Pie
            data={normalized}
            dataKey="value"
            nameKey="name"
            innerRadius={size / 2 - thickness}
            outerRadius={size / 2 - 4}
            onMouseEnter={(_, index) => {
              const item = normalized[index ?? 0];
              if (item) pieProps?.onMouseEnter?.(item);
            }}
            onMouseLeave={pieProps?.onMouseLeave}
            paddingAngle={pieProps?.paddingAngle ?? paddingAngle}
            cornerRadius={pieProps?.cornerRadius}>
            {normalized.map((entry) => (
              <Cell key={entry.name} fill={entry.color ?? '#6366f1'} />
            ))}
          </Pie>
          <Tooltip formatter={(value) => value} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
