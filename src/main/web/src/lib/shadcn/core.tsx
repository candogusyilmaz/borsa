import {
  type CSSProperties,
  type ChangeEvent,
  cloneElement,
  createContext,
  type MouseEventHandler,
  type ReactElement,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState
} from 'react';

type ThemeProviderProps = {
  children: ReactNode;
  defaultColorScheme?: 'light' | 'dark';
};

type ColorScheme = 'light' | 'dark';

const ColorSchemeContext = createContext<{
  colorScheme: ColorScheme;
  setColorScheme: (value: ColorScheme) => void;
}>({
  colorScheme: 'dark',
  setColorScheme: () => undefined
});

function applyTheme(colorScheme: ColorScheme) {
  const html = document.documentElement;
  html.dataset.theme = colorScheme;
  html.classList.toggle('dark', colorScheme === 'dark');
}

export function ThemeProvider({ children, defaultColorScheme = 'dark' }: ThemeProviderProps) {
  const [colorScheme, setColorSchemeState] = useState<ColorScheme>(() => {
    if (typeof window === 'undefined') return defaultColorScheme;
    const stored = window.localStorage.getItem('theme-scheme');
    if (stored === 'dark' || stored === 'light') return stored;
    return defaultColorScheme;
  });

  useEffect(() => {
    applyTheme(colorScheme);
    window.localStorage.setItem('theme-scheme', colorScheme);
  }, [colorScheme]);

  return (
    <ColorSchemeContext.Provider value={{ colorScheme, setColorScheme: setColorSchemeState }}>{children}</ColorSchemeContext.Provider>
  );
}

export function useThemeMode() {
  return useContext(ColorSchemeContext);
}

const SPACING: Record<string, string> = {
  xs: '0.5rem',
  sm: '0.75rem',
  md: '1rem',
  lg: '1.25rem',
  xl: '1.5rem'
};

function toCssSize(value: unknown) {
  if (typeof value === 'number') return `${value}px`;
  if (typeof value === 'string') return SPACING[value] ?? value;
  return undefined;
}

function toColor(value: unknown) {
  if (typeof value !== 'string') return undefined;
  if (value === 'dimmed') return 'var(--text-muted)';
  if (value.startsWith('red')) return '#ef4444';
  if (value.startsWith('teal') || value.startsWith('green')) return '#14b8a6';
  if (value.startsWith('blue') || value.startsWith('accent')) return '#6366f1';
  if (value === 'gray') return '#94a3b8';
  if (value === 'dark') return '#334155';
  return value;
}

function remValue(value: number | string) {
  if (typeof value === 'number') return `${value / 16}rem`;
  return value;
}

export function rem(value: number | string) {
  return remValue(value);
}

type PrimitiveProps<T = HTMLDivElement> = {
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
  c?: string;
  bg?: string;
  p?: string | number;
  px?: string | number;
  py?: string | number;
  pt?: string | number;
  pr?: string | number;
  pb?: string | number;
  pl?: string | number;
  m?: string | number;
  mx?: string | number;
  my?: string | number;
  mt?: string | number;
  mr?: string | number;
  mb?: string | number;
  ml?: string | number;
  w?: string | number;
  h?: string | number;
  maw?: string | number;
  mah?: string | number;
  miw?: string | number;
  mih?: string | number;
  ta?: CSSProperties['textAlign'];
  fw?: CSSProperties['fontWeight'];
  fz?: CSSProperties['fontSize'];
  lh?: CSSProperties['lineHeight'];
  radius?: string | number;
  bdrs?: string | number;
  pos?: CSSProperties['position'];
  top?: string | number;
  left?: string | number;
  right?: string | number;
  bottom?: string | number;
  flex?: string | number;
  [key: string]: unknown;
} & Omit<React.HTMLAttributes<T>, 'color'>;

function withStyleProps<T>(props: PrimitiveProps<T>) {
  const {
    c,
    bg,
    p,
    px,
    py,
    pt,
    pr,
    pb,
    pl,
    m,
    mx,
    my,
    mt,
    mr,
    mb,
    ml,
    w,
    h,
    maw,
    mah,
    miw,
    mih,
    ta,
    fw,
    fz,
    lh,
    radius,
    bdrs,
    pos,
    top,
    left,
    right,
    bottom,
    flex,
    style,
    ...rest
  } = props;

  const mergedStyle: CSSProperties = {
    color: toColor(c),
    background: toColor(bg),
    padding: toCssSize(p),
    paddingInline: toCssSize(px),
    paddingBlock: toCssSize(py),
    paddingTop: toCssSize(pt),
    paddingRight: toCssSize(pr),
    paddingBottom: toCssSize(pb),
    paddingLeft: toCssSize(pl),
    margin: toCssSize(m),
    marginInline: toCssSize(mx),
    marginBlock: toCssSize(my),
    marginTop: toCssSize(mt),
    marginRight: toCssSize(mr),
    marginBottom: toCssSize(mb),
    marginLeft: toCssSize(ml),
    width: toCssSize(w),
    height: toCssSize(h),
    maxWidth: toCssSize(maw),
    maxHeight: toCssSize(mah),
    minWidth: toCssSize(miw),
    minHeight: toCssSize(mih),
    textAlign: ta,
    fontWeight: fw,
    fontSize: typeof fz === 'number' ? remValue(fz) : fz,
    lineHeight: lh,
    borderRadius: toCssSize(radius ?? bdrs),
    position: pos,
    top: toCssSize(top),
    left: toCssSize(left),
    right: toCssSize(right),
    bottom: toCssSize(bottom),
    flex: typeof flex === 'number' ? `${flex}` : flex,
    ...style
  };

  return { rest, style: mergedStyle };
}

export function Box(props: PrimitiveProps) {
  const { rest, style } = withStyleProps(props);
  return <div {...rest} style={style} />;
}

export type TextProps = PrimitiveProps<HTMLSpanElement> & {
  span?: boolean;
  size?: string | number;
  inherit?: boolean;
};

export function Text({ span, size, style, ...props }: TextProps) {
  const { rest, style: mergedStyle } = withStyleProps({ ...props, fz: props.fz ?? size, style });
  if (span) {
    return <span {...(rest as React.HTMLAttributes<HTMLSpanElement>)} style={mergedStyle} />;
  }
  return <p {...(rest as React.HTMLAttributes<HTMLParagraphElement>)} style={{ margin: 0, ...mergedStyle }} />;
}

export function Title({ order = 2, ...props }: PrimitiveProps & { order?: 1 | 2 | 3 | 4 | 5 | 6 }) {
  const { rest, style } = withStyleProps(props);
  const Tag = `h${order}` as const;
  return <Tag {...rest} style={{ margin: 0, ...style }} />;
}

export function Group({
  justify,
  align,
  gap,
  wrap,
  children,
  style,
  ...props
}: PrimitiveProps & {
  justify?: CSSProperties['justifyContent'];
  align?: CSSProperties['alignItems'];
  gap?: string | number;
  wrap?: CSSProperties['flexWrap'];
}) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <div
      {...rest}
      style={{ display: 'flex', alignItems: align, justifyContent: justify, gap: toCssSize(gap), flexWrap: wrap, ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

export function Flex({ direction, ...props }: PrimitiveProps & { direction?: CSSProperties['flexDirection'] | Record<string, string> }) {
  const resolvedDirection = typeof direction === 'string' ? direction : direction?.base;
  return <Group {...props} style={{ ...(props.style as CSSProperties), flexDirection: resolvedDirection as CSSProperties['flexDirection'] }} />;
}

export function Stack({ children, gap = 'md', justify, align, style, ...props }: PrimitiveProps & { gap?: string | number; justify?: CSSProperties['justifyContent']; align?: CSSProperties['alignItems'] }) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <div
      {...rest}
      style={{ display: 'flex', flexDirection: 'column', gap: toCssSize(gap), justifyContent: justify, alignItems: align, ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

export type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & PrimitiveProps<HTMLButtonElement> & {
  variant?: 'filled' | 'subtle' | 'default' | 'light' | 'transparent' | string;
  leftSection?: ReactNode;
  rightSection?: ReactNode;
  loading?: boolean;
};

const baseButtonStyle: CSSProperties = {
  border: '1px solid transparent',
  borderRadius: '0.6rem',
  padding: '0.5rem 0.9rem',
  cursor: 'pointer',
  fontWeight: 600,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: '0.4rem'
};

function buttonVariant(variant?: string): CSSProperties {
  if (variant === 'subtle' || variant === 'light') return { background: 'transparent', color: 'var(--accent-color)', borderColor: 'var(--border-color)' };
  if (variant === 'default') return { background: 'var(--card-bg)', color: 'var(--text-main)', borderColor: 'var(--border-color)' };
  return { background: 'var(--btn-primary)', color: '#fff' };
}

export const Button: any = ({ leftSection, rightSection, children, variant, loading, disabled, style, ...props }: ButtonProps) => {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <button
      {...(rest as React.ButtonHTMLAttributes<HTMLButtonElement>)}
      disabled={disabled || loading}
      className={`rounded-xl backdrop-blur-lg transition-all duration-200 ${rest.className ?? ''}`}
      style={{ ...baseButtonStyle, ...buttonVariant(variant), opacity: disabled || loading ? 0.6 : 1, ...mergedStyle, ...style }}>
      {leftSection}
      {loading ? 'Loading...' : children}
      {rightSection}
    </button>
  );
};

Button.Group = ({ children }: { children?: ReactNode }) => <Group>{children}</Group>;

export const ActionIcon: any = ({ children, style, ...props }: ButtonProps) => {
  return (
    <Button
      {...props}
      style={{ width: '2rem', height: '2rem', padding: 0, borderRadius: '999px', ...style }}
      variant={props.variant ?? 'subtle'}>
      {children}
    </Button>
  );
};

export type CardProps = PrimitiveProps & {
  withBorder?: boolean;
  shadow?: string;
  padding?: string | number;
};

export function Card({ withBorder, padding, children, style, ...props }: CardProps) {
  const { rest, style: mergedStyle } = withStyleProps({ ...props, p: props.p ?? padding, style });
  return (
    <div
      {...rest}
      className={`rounded-2xl border border-white/20 bg-white/10 backdrop-blur-xl dark:bg-white/5 ${rest.className ?? ''}`}
      style={{
        background: 'var(--card-bg)',
        border: withBorder ? '1px solid var(--border-color)' : undefined,
        borderRadius: '0.9rem',
        boxShadow: 'var(--card-shadow)',
        ...mergedStyle
      }}>
      {children}
    </div>
  );
}

export function Container({ children, style, ...props }: PrimitiveProps) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <div {...rest} style={{ width: '100%', maxWidth: '1200px', marginInline: 'auto', ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

export function Center({ children, style, ...props }: PrimitiveProps) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <div {...rest} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

export function Divider({ label, labelPosition = 'left', my, style, ...props }: PrimitiveProps & { label?: ReactNode; labelPosition?: 'left' | 'center' | 'right' }) {
  const { rest, style: mergedStyle } = withStyleProps({ ...props, my });
  if (!label) {
    return <hr {...rest} style={{ border: 0, borderTop: '1px solid var(--border-color)', ...mergedStyle, ...style }} />;
  }
  return (
    <div {...rest} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', ...mergedStyle, ...style }}>
      {(labelPosition === 'center' || labelPosition === 'right') && <div style={{ flex: 1, borderTop: '1px solid var(--border-color)' }} />}
      <span>{label}</span>
      {(labelPosition === 'center' || labelPosition === 'left') && <div style={{ flex: 1, borderTop: '1px solid var(--border-color)' }} />}
    </div>
  );
}

export function ThemeIcon({ children, size = 32, radius, variant, c, style, ...props }: PrimitiveProps & { size?: number | string; variant?: string }) {
  const { rest, style: mergedStyle } = withStyleProps({ ...props, c, radius, style });
  return (
    <span
      {...rest}
      style={{
        width: toCssSize(size),
        height: toCssSize(size),
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: toCssSize(radius ?? '999px'),
        background: variant === 'transparent' ? 'transparent' : 'color-mix(in srgb, var(--card-bg) 85%, var(--accent-color) 15%)',
        ...mergedStyle
      }}>
      {children}
    </span>
  );
}

export function Badge({ children, variant, style, ...props }: PrimitiveProps & { variant?: string }) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <span
      {...rest}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '0.2rem 0.5rem',
        borderRadius: '999px',
        fontSize: '0.75rem',
        border: variant === 'outline' ? '1px solid var(--border-color)' : undefined,
        background: variant === 'outline' ? 'transparent' : 'color-mix(in srgb, var(--accent-color) 16%, transparent)',
        ...mergedStyle,
        ...style
      }}>
      {children}
    </span>
  );
}

export function ColorSwatch({ color, size = 12, radius = '999px' }: { color: string; size?: number; radius?: string | number }) {
  return <span style={{ display: 'inline-block', width: size, height: size, borderRadius: toCssSize(radius), background: color }} />;
}

export function Avatar({ src, size = 32, radius = '999px', alt = 'User avatar' }: { src?: string; size?: number; radius?: string | number; alt?: string }) {
  return <img src={src} alt={alt} style={{ width: size, height: size, borderRadius: toCssSize(radius), objectFit: 'cover' }} />;
}

export function UnstyledButton({ children, style, ...props }: ButtonProps) {
  return (
    <button {...props} style={{ background: 'transparent', border: 0, padding: 0, cursor: 'pointer', ...style }}>
      {children}
    </button>
  );
}

export function Loader({ size = 20 }: { size?: number | string }) {
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: '999px',
        border: '2px solid var(--border-color)',
        borderTopColor: 'var(--accent-color)',
        display: 'inline-block',
        animation: 'spin 0.8s linear infinite'
      }}
    />
  );
}

export function Alert({ children, style, ...props }: PrimitiveProps) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <div {...rest} style={{ border: '1px solid var(--border-color)', borderRadius: '0.75rem', padding: '0.75rem', ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

export function SimpleGrid({ cols = 1, spacing = 'md', children, style, ...props }: PrimitiveProps & { cols?: number | Record<string, number>; spacing?: string | number }) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  const resolvedCols = typeof cols === 'number' ? cols : (cols.sm ?? cols.xs ?? cols.base ?? 1);
  return (
    <div {...rest} style={{ display: 'grid', gridTemplateColumns: `repeat(${resolvedCols}, minmax(0,1fr))`, gap: toCssSize(spacing), ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

type TableComponent = React.FC<PrimitiveProps<HTMLTableElement>> & {
  Thead: React.FC<PrimitiveProps<HTMLTableSectionElement>>;
  Tbody: React.FC<PrimitiveProps<HTMLTableSectionElement>>;
  Tr: React.FC<PrimitiveProps<HTMLTableRowElement>>;
  Th: React.FC<PrimitiveProps<HTMLTableCellElement>>;
  Td: React.FC<PrimitiveProps<HTMLTableCellElement>>;
  ScrollContainer: React.FC<PrimitiveProps>;
};

const TableBase: TableComponent = (({ children, style, ...props }) => {
  const { rest, style: mergedStyle } = withStyleProps(props as PrimitiveProps<HTMLTableElement>);
  return (
    <table {...(rest as React.TableHTMLAttributes<HTMLTableElement>)} style={{ width: '100%', borderCollapse: 'collapse', ...mergedStyle, ...style }}>
      {children}
    </table>
  );
}) as TableComponent;

TableBase.Thead = ({ children, ...props }) => <thead {...(props as React.HTMLAttributes<HTMLTableSectionElement>)}>{children}</thead>;
TableBase.Tbody = ({ children, ...props }) => <tbody {...(props as React.HTMLAttributes<HTMLTableSectionElement>)}>{children}</tbody>;
TableBase.Tr = ({ children, ...props }) => <tr {...(props as React.HTMLAttributes<HTMLTableRowElement>)}>{children}</tr>;
TableBase.Th = ({ children, style, ...props }) => (
  <th {...(props as React.ThHTMLAttributes<HTMLTableCellElement>)} style={{ textAlign: 'left', padding: '0.6rem', borderBottom: '1px solid var(--border-color)', ...style }}>
    {children}
  </th>
);
TableBase.Td = ({ children, style, ...props }) => (
  <td {...(props as React.TdHTMLAttributes<HTMLTableCellElement>)} style={{ padding: '0.6rem', borderBottom: '1px solid var(--border-color)', ...style }}>
    {children}
  </td>
);
TableBase.ScrollContainer = ({ children, ...props }) => {
  const { rest, style } = withStyleProps(props);
  return (
    <div {...rest} style={{ overflowX: 'auto', ...style }}>
      {children}
    </div>
  );
};

export const Table = TableBase;

export function Pagination({ total = 1, value = 1, onChange }: { total?: number; value?: number; onChange?: (value: number) => void }) {
  const pages = Array.from({ length: Math.max(total, 1) }, (_, i) => i + 1);
  return (
    <Group gap="xs">
      <Button variant="subtle" disabled={value <= 1} onClick={() => onChange?.(Math.max(1, value - 1))}>
        Prev
      </Button>
      {pages.map((page) => (
        <Button key={page} variant={page === value ? 'filled' : 'subtle'} onClick={() => onChange?.(page)}>
          {page}
        </Button>
      ))}
      <Button variant="subtle" disabled={value >= total} onClick={() => onChange?.(Math.min(total, value + 1))}>
        Next
      </Button>
    </Group>
  );
}

export const Modal: any = ({ opened, onClose, title, children, size, centered = true }: { opened: boolean; onClose: () => void; title?: ReactNode; children: ReactNode; size?: string | number; centered?: boolean; [key: string]: unknown }) => {
  if (!opened) return null;
  return (
    <button
      type="button"
      style={{ position: 'fixed', inset: 0, zIndex: 999, background: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: centered ? 'center' : 'flex-start', justifyContent: 'center', padding: '2rem' }}
      onClick={onClose}
      aria-label="Close modal"
      >
      <div
        className="modal-content"
        style={{ width: toCssSize(size) ?? 'min(700px, 100%)', borderRadius: '1rem', padding: '1rem' }}
        onClick={(event) => event.stopPropagation()}
        onKeyDown={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true">
        {title && <Title order={4} mb="md">{title}</Title>}
        {children}
      </div>
    </button>
  );
};

const DrawerCtx = createContext<{ onClose: () => void }>({ onClose: () => undefined });

const DrawerRoot = ({ opened, onClose, children }: { opened: boolean; onClose: () => void; children: ReactNode; [key: string]: unknown }) => {
  if (!opened) return null;
  return <DrawerCtx.Provider value={{ onClose }}>{children}</DrawerCtx.Provider>;
};

const DrawerOverlay = () => {
  const { onClose } = useContext(DrawerCtx);
  return (
    <button
      type="button"
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', zIndex: 998, border: 0, padding: 0 }}
      onClick={onClose}
      aria-label="Close drawer"
    />
  );
};

const DrawerContent = ({ children }: { children: ReactNode }) => (
  <div className="border-r border-white/20 bg-white/10 backdrop-blur-2xl dark:bg-white/5" style={{ position: 'fixed', left: 0, top: 0, bottom: 0, width: '18rem', zIndex: 999, background: 'var(--card-bg)' }}>{children}</div>
);

export const Drawer: any = {
  Root: DrawerRoot,
  Overlay: DrawerOverlay,
  Content: DrawerContent
};

type MenuContextType = {
  opened: boolean;
  setOpened: (value: boolean) => void;
};

const MenuContext = createContext<MenuContextType>({ opened: false, setOpened: () => undefined });

function MenuRoot({ children }: { children: ReactNode; [key: string]: unknown }) {
  const [opened, setOpened] = useState(false);
  return (
    <MenuContext.Provider value={{ opened, setOpened }}>
      <div style={{ position: 'relative' }}>{children}</div>
    </MenuContext.Provider>
  );
}

function MenuTarget({ children }: { children: ReactElement }) {
  const { setOpened } = useContext(MenuContext);
  const child = children as ReactElement<{ onClick?: MouseEventHandler }>;
  return cloneElement(child, {
    onClick: ((event: unknown) => {
      child.props.onClick?.(event as never);
      setOpened(true);
    }) as MouseEventHandler
  });
}

function MenuDropdown({ children, ...props }: PrimitiveProps) {
  const { opened } = useContext(MenuContext);
  if (!opened) return null;
  return (
    <div
      {...props}
      style={{
        position: 'absolute',
        right: 0,
        marginTop: '0.5rem',
        minWidth: '220px',
        border: '1px solid var(--border-color)',
        borderRadius: '0.75rem',
        background: 'var(--card-bg)',
        zIndex: 20,
        padding: '0.4rem'
      }}>
      {children}
    </div>
  );
}

function MenuItem({ children, leftSection, onClick, disabled }: PrimitiveProps & { leftSection?: ReactNode; disabled?: boolean }) {
  const { setOpened } = useContext(MenuContext);
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={(event) => {
        (onClick as MouseEventHandler | undefined)?.(event as never);
        setOpened(false);
      }}
      style={{ width: '100%', display: 'flex', alignItems: 'center', gap: '0.5rem', border: 0, background: 'transparent', textAlign: 'left', padding: '0.45rem 0.55rem', borderRadius: '0.5rem' }}>
      {leftSection}
      {children}
    </button>
  );
}

export const Menu: any = Object.assign(MenuRoot as any, {
  Target: MenuTarget,
  Dropdown: MenuDropdown,
  Item: MenuItem,
  Divider: () => <hr style={{ border: 0, borderTop: '1px solid var(--border-color)', margin: '0.35rem 0' }} />,
  Label: ({ children }: { children: ReactNode }) => (
    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', padding: '0.35rem 0.55rem' }}>{children}</div>
  )
});

export function ScrollArea({ children, style, ...props }: PrimitiveProps) {
  const { rest, style: mergedStyle } = withStyleProps(props);
  return (
    <div {...rest} style={{ overflow: 'auto', ...mergedStyle, ...style }}>
      {children}
    </div>
  );
}

ScrollArea.Autosize = function Autosize({ children, mah, ...props }: PrimitiveProps) {
  return (
    <ScrollArea {...props} style={{ maxHeight: toCssSize(mah) }}>
      {children}
    </ScrollArea>
  );
};

type Option = string | { value: string; label: string };

function normalizeOptions(data?: Option[]) {
  return (data ?? []).map((item) => (typeof item === 'string' ? { value: item, label: item } : item));
}

export const Select: any = ({ data, value, onChange, placeholder, ...props }: PrimitiveProps<HTMLSelectElement> & { data?: Option[]; value?: string | number | null; onChange?: (value: string | null) => void; placeholder?: string; }) => {
  const options = normalizeOptions(data);
  const { rest, style } = withStyleProps(props as PrimitiveProps<HTMLSelectElement>);
  return (
    <select
      {...(rest as React.SelectHTMLAttributes<HTMLSelectElement>)}
      value={value ?? ''}
      onChange={(e) => onChange?.(e.target.value || null)}
      style={{ border: '1px solid var(--border-color)', borderRadius: '0.6rem', padding: '0.5rem', background: 'transparent', color: 'inherit', ...style }}>
      <option value="">{placeholder ?? 'Select'}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
};

export const NativeSelect: any = Select;

export const TextInput: any = ({ value, onChange, ...props }: any) => {
  const { rest, style } = withStyleProps(props as PrimitiveProps<HTMLInputElement>);
  return (
    <input
      {...(rest as React.InputHTMLAttributes<HTMLInputElement>)}
      value={value ?? ''}
      onChange={(event) => onChange?.(event)}
      className="input-base"
      style={{ border: '1px solid var(--border-color)', borderRadius: '0.75rem', padding: '0.55rem 0.75rem', width: '100%', background: 'transparent', color: 'inherit', ...style }}
    />
  );
};

export const PasswordInput: any = (props: any) => {
  return <TextInput {...(props as object)} type="password" />;
}

export const NumberInput: any = ({ value, onChange, ...props }: any) => {
  const { rest, style } = withStyleProps(props as PrimitiveProps<HTMLInputElement>);
  return (
    <input
      {...(rest as React.InputHTMLAttributes<HTMLInputElement>)}
      type="number"
      value={value ?? ''}
      onChange={(event) => onChange?.(Number(event.target.value))}
      style={{ border: '1px solid var(--border-color)', borderRadius: '0.75rem', padding: '0.55rem 0.75rem', width: '100%', background: 'transparent', color: 'inherit', ...style }}
    />
  );
};

const CheckboxGroupContext = createContext<{ values: string[]; onChange: (next: string[]) => void } | null>(null);

function CheckboxGroup({ children, value, onChange }: { children: ReactNode; value?: string[] | string | number; onChange?: (value: string[]) => void; [key: string]: unknown }) {
  return (
    <CheckboxGroupContext.Provider value={{ values: Array.isArray(value) ? value : [], onChange: onChange ?? (() => undefined) }}>
      <Stack gap="xs">{children}</Stack>
    </CheckboxGroupContext.Provider>
  );
}

export const Checkbox: any = ({ checked, value, onChange, label, children, ...props }: PrimitiveProps<HTMLInputElement> & { checked?: boolean; value?: string; label?: ReactNode; onChange?: (event: ChangeEvent<HTMLInputElement>) => void; }) => {
  const group = useContext(CheckboxGroupContext);
  const isGroupItem = Boolean(group && value);
  const finalChecked = isGroupItem ? group?.values.includes(value!) : checked;
  return (
    <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
      <input
        {...(props as React.InputHTMLAttributes<HTMLInputElement>)}
        type="checkbox"
        checked={finalChecked}
        value={value}
        onChange={(event) => {
          if (isGroupItem) {
            const next = event.target.checked ? [...(group?.values ?? []), value!] : (group?.values ?? []).filter((item) => item !== value);
            group?.onChange(next);
          }
          onChange?.(event);
        }}
      />
      <span>{label ?? children}</span>
    </label>
  );
};

Checkbox.Group = CheckboxGroup;

const RadioContext = createContext<{ value: string | null; onChange: (value: string) => void }>({ value: null, onChange: () => undefined });

function RadioGroup({ value, onChange, children }: { value?: string | null; onChange?: (value: string) => void; children: ReactNode; [key: string]: unknown }) {
  return <RadioContext.Provider value={{ value: value ?? null, onChange: onChange ?? (() => undefined) }}>{children}</RadioContext.Provider>;
}

function RadioCard({ value, children }: { value: string; children: ReactNode; [key: string]: unknown }) {
  const ctx = useContext(RadioContext);
  return (
    <button
      type="button"
      onClick={() => ctx.onChange(value)}
      style={{
        border: '1px solid var(--border-color)',
        borderRadius: '0.75rem',
        padding: '0.75rem',
        background: ctx.value === value ? 'color-mix(in srgb, var(--accent-color) 15%, transparent)' : 'transparent',
        cursor: 'pointer',
        width: '100%',
        textAlign: 'left'
      }}>
      {children}
    </button>
  );
}

export const Radio: any = Object.assign(() => null, {
  Group: RadioGroup,
  Card: RadioCard
});

export function Skeleton({ height = 20 }: { height?: number | string }) {
  return <div style={{ width: '100%', height: toCssSize(height), borderRadius: '0.75rem', background: 'color-mix(in srgb, var(--card-bg) 65%, #fff 10%)' }} />;
}

export const SegmentedControl: any = ({ data, value, onChange, ...props }: { data: { label: string; value: string }[]; value: string; onChange: (value: string) => void; [key: string]: unknown }) => {
  return (
    <Group {...props} gap="xs" style={{ padding: '0.2rem', borderRadius: '0.7rem', border: '1px solid var(--border-color)', width: 'fit-content', ...(props.style as CSSProperties) }}>
      {data.map((item) => (
        <Button key={item.value} variant={item.value === value ? 'filled' : 'subtle'} onClick={() => onChange(item.value)}>
          {item.label}
        </Button>
      ))}
    </Group>
  );
};

export const Tooltip: any = ({ label, children }: { label: ReactNode; children: ReactElement; [key: string]: unknown }) => {
  return cloneElement(children as ReactElement<{ title?: string }>, { title: typeof label === 'string' ? label : undefined });
};

const BREAKPOINTS: Record<string, number> = {
  base: 0,
  xs: 576,
  sm: 768,
  md: 992,
  lg: 1200,
  xl: 1408
};

export function useMatches<T>(values: Record<string, T>) {
  const [width, setWidth] = useState(typeof window === 'undefined' ? 0 : window.innerWidth);
  useEffect(() => {
    const listener = () => setWidth(window.innerWidth);
    window.addEventListener('resize', listener);
    return () => window.removeEventListener('resize', listener);
  }, []);

  return useMemo(() => {
    const entries = Object.entries(values).sort((a, b) => (BREAKPOINTS[a[0]] ?? 0) - (BREAKPOINTS[b[0]] ?? 0));
    let resolved = entries[0]?.[1];
    for (const [key, val] of entries) {
      if (width >= (BREAKPOINTS[key] ?? 0)) resolved = val;
    }
    return resolved as T;
  }, [values, width]);
}

if (typeof document !== 'undefined' && !document.getElementById('shadcn-replacement-style')) {
  const style = document.createElement('style');
  style.id = 'shadcn-replacement-style';
  style.innerHTML = '@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }';
  document.head.appendChild(style);
}

export default {};
