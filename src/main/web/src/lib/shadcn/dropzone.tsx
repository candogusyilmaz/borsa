import { createContext, type DragEvent, type ReactNode, useContext, useState } from 'react';

type DropzoneState = 'idle' | 'accept' | 'reject';

const DropzoneContext = createContext<DropzoneState>('idle');

type DropzoneProps = {
  children: ReactNode;
  onDrop?: (files: File[]) => void;
  maxFiles?: number;
  maxSize?: number;
  loading?: boolean;
};

function DropzoneRoot({ children, onDrop, maxFiles = Number.POSITIVE_INFINITY, maxSize = Number.POSITIVE_INFINITY, loading }: DropzoneProps) {
  const [state, setState] = useState<DropzoneState>('idle');

  const extractFiles = (event: DragEvent<HTMLElement>) => {
    const all = Array.from(event.dataTransfer.files);
    const valid = all.filter((file) => file.size <= maxSize).slice(0, maxFiles);
    setState(valid.length > 0 ? 'accept' : 'reject');
    return valid;
  };

  return (
    <DropzoneContext.Provider value={state}>
      <button
        type="button"
        onDragOver={(event) => {
          event.preventDefault();
          setState('accept');
        }}
        onDragLeave={() => setState('idle')}
        onDrop={(event) => {
          event.preventDefault();
          const files = extractFiles(event);
          onDrop?.(files);
          setState('idle');
        }}
        style={{
          border: '1px dashed var(--border-color)',
          borderRadius: 12,
          padding: 16,
          opacity: loading ? 0.6 : 1
        }}
        aria-label="Drop files here or click to select"
        >
        {children}
      </button>
    </DropzoneContext.Provider>
  );
}

function Conditional({ when, children }: { when: DropzoneState; children: ReactNode }) {
  const state = useContext(DropzoneContext);
  if (state !== when) return null;
  return <>{children}</>;
}

export const Dropzone = Object.assign(DropzoneRoot, {
  Accept: ({ children }: { children: ReactNode }) => <Conditional when="accept">{children}</Conditional>,
  Reject: ({ children }: { children: ReactNode }) => <Conditional when="reject">{children}</Conditional>,
  Idle: ({ children }: { children: ReactNode }) => <Conditional when="idle">{children}</Conditional>
});
