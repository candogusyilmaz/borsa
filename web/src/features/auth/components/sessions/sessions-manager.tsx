import {
  Alert,
  Badge,
  Button,
  CopyButton,
  Group,
  Loader,
  Skeleton,
  Stack,
  Switch,
  Text,
  Title,
  Tooltip,
  UnstyledButton
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  ArrowClockwiseIcon,
  CaretDownIcon,
  CaretUpIcon,
  CheckIcon,
  CopyIcon,
  DeviceMobileIcon,
  DevicesIcon,
  DeviceTabletIcon,
  GlobeSimpleIcon,
  LaptopIcon,
  ShieldCheckIcon,
  ShieldWarningIcon,
  SignOutIcon,
  TrashSimpleIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useMemo, useState } from 'react';
import { $api, client } from '@/api/client';
import { showApiError } from '@/api/errors';
import type { components } from '@/api/schema';
import { useAuth } from '@/shared/hooks/use-auth';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import classes from './sessions.module.css';

type DeviceSession = components['schemas']['DeviceSessionResponse'];

export function getDeviceIcon(label?: string) {
  if (!label) return GlobeSimpleIcon;
  const lower = label.toLowerCase();
  if (lower.includes('phone') || lower.includes('mobile') || lower.includes('ios') || lower.includes('android')) {
    return DeviceMobileIcon;
  }
  if (lower.includes('tablet') || lower.includes('ipad')) {
    return DeviceTabletIcon;
  }
  if (lower.includes('desktop') || lower.includes('mac') || lower.includes('windows') || lower.includes('linux') || lower.includes('pc')) {
    return LaptopIcon;
  }
  return GlobeSimpleIcon;
}

export function formatDate(isoString?: string): string {
  if (!isoString) return 'N/A';
  try {
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return 'N/A';
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      hour12: true
    }).format(date);
  } catch {
    return isoString;
  }
}

export function formatRelativeTime(isoString?: string): string {
  if (!isoString) return 'Not yet used';
  try {
    const date = new Date(isoString);
    const now = new Date();
    const diffSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (diffSeconds < 60) {
      return 'Active just now';
    }
    const diffMinutes = Math.floor(diffSeconds / 60);
    if (diffMinutes < 60) {
      return `${diffMinutes}m ago`;
    }
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
      return `${diffHours}h ago`;
    }
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) {
      return `${diffDays}d ago`;
    }
    return formatDate(isoString);
  } catch {
    return isoString;
  }
}

interface RevokeSessionOverlayProps {
  familyId: string;
  deviceLabel?: string;
  isCurrent: boolean;
}

function RevokeSessionConfirmation({ familyId, deviceLabel, isCurrent }: RevokeSessionOverlayProps) {
  const overlay = useCurrentOverlay();
  const queryClient = useQueryClient();
  const { logout } = useAuth();
  const navigate = useNavigate();

  const revokeMutation = $api.useMutation('delete', '/api/v1/auth/sessions/{familyId}', {
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/auth/sessions'] });
      notifications.show({
        title: 'Session Revoked',
        message: 'The selected device session has been signed out.',
        color: 'green'
      });
      overlay.complete();
      if (isCurrent) {
        await logout();
        await navigate({ to: '/login', replace: true });
      }
    },
    onError: (err) => {
      showApiError(err, {
        title: 'Revocation Failed',
        fallbackMessage: 'Could not revoke session. Please try again.'
      });
    }
  });

  return (
    <Stack gap="md">
      <Text size="sm">
        Are you sure you want to revoke access for <strong>{deviceLabel || 'this device'}</strong>?
      </Text>
      <Text size="xs" c="dimmed">
        This device will be immediately signed out and all access or refresh tokens issued to it will become invalid.
      </Text>

      <Group justify="flex-end" gap="sm" mt="sm">
        <Button variant="default" onClick={() => overlay.dismiss('cancelled')}>
          Cancel
        </Button>
        <Button
          color="red"
          loading={revokeMutation.isPending}
          onClick={() => revokeMutation.mutate({ params: { path: { familyId } } })}
          leftSection={<TrashSimpleIcon size={16} weight="bold" />}>
          Revoke Session
        </Button>
      </Group>
    </Stack>
  );
}

export const RevokeSessionOverlay = registerOverlay(RevokeSessionConfirmation, {
  name: 'revoke-session',
  title: 'Revoke Device Session',
  presentation: 'modal',
  size: 'md'
});

interface TerminateOthersOverlayProps {
  familyIds: string[];
}

function TerminateOthersConfirmation({ familyIds }: TerminateOthersOverlayProps) {
  const overlay = useCurrentOverlay();
  const queryClient = useQueryClient();
  const [isTerminating, setIsTerminating] = useState(false);

  async function handleConfirm() {
    if (familyIds.length === 0) return;
    setIsTerminating(true);

    try {
      await Promise.all(
        familyIds.map((familyId) =>
          client.DELETE('/api/v1/auth/sessions/{familyId}', {
            params: { path: { familyId } }
          })
        )
      );
      await queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/auth/sessions'] });
      notifications.show({
        title: 'Other Sessions Terminated',
        message: `Successfully signed out of ${familyIds.length} other active session(s).`,
        color: 'green'
      });
      overlay.complete();
    } catch (error) {
      showApiError(error, {
        title: 'Termination Incomplete',
        fallbackMessage: 'Some sessions could not be terminated. Refreshing list...'
      });
      void queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/auth/sessions'] });
    } finally {
      setIsTerminating(false);
    }
  }

  return (
    <Stack gap="md">
      <Text size="sm">
        Are you sure you want to terminate <strong>{familyIds.length} other session(s)</strong>?
      </Text>
      <Text size="xs" c="dimmed">
        Your current device will remain signed in. All other phones, tablets, and computers connected to your account will be signed out
        immediately.
      </Text>

      <Group justify="flex-end" gap="sm" mt="sm">
        <Button variant="default" onClick={() => overlay.dismiss('cancelled')}>
          Cancel
        </Button>
        <Button color="red" loading={isTerminating} onClick={handleConfirm} leftSection={<ShieldWarningIcon size={16} weight="bold" />}>
          Terminate Others
        </Button>
      </Group>
    </Stack>
  );
}

export const TerminateOthersOverlay = registerOverlay(TerminateOthersConfirmation, {
  name: 'terminate-others',
  title: 'Terminate All Other Sessions',
  presentation: 'modal',
  size: 'md'
});

function SignOutCurrentConfirmation() {
  const overlay = useCurrentOverlay();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [isSigningOut, setIsSigningOut] = useState(false);

  async function handleConfirm() {
    setIsSigningOut(true);
    try {
      await logout();
      notifications.show({
        title: 'Signed Out',
        message: 'You have been safely signed out of this device.',
        color: 'blue'
      });
      overlay.complete();
      await navigate({ to: '/login', replace: true });
    } catch (error) {
      showApiError(error, {
        title: 'Sign Out Error',
        fallbackMessage: 'An error occurred while signing out.'
      });
      setIsSigningOut(false);
    }
  }

  return (
    <Stack gap="md">
      <Text size="sm">Are you sure you want to sign out of this device?</Text>
      <Text size="xs" c="dimmed">
        You will be redirected to the login page and will need to sign in again to access your portfolio.
      </Text>

      <Group justify="flex-end" gap="sm" mt="sm">
        <Button variant="default" onClick={() => overlay.dismiss('cancelled')}>
          Cancel
        </Button>
        <Button color="red" loading={isSigningOut} onClick={handleConfirm} leftSection={<SignOutIcon size={16} weight="bold" />}>
          Sign Out
        </Button>
      </Group>
    </Stack>
  );
}

export const SignOutCurrentOverlay = registerOverlay(SignOutCurrentConfirmation, {
  name: 'sign-out-current',
  title: 'Sign Out of Current Device',
  presentation: 'modal',
  size: 'md'
});

interface SessionCardProps {
  session: DeviceSession;
  onRevokeClick: (session: DeviceSession) => void;
  onSignOutCurrent: () => void;
  isHistory?: boolean;
}

function SessionCard({ session, onRevokeClick, onSignOutCurrent, isHistory }: SessionCardProps) {
  const [detailsOpen, setDetailsOpen] = useState(false);
  const IconComponent = getDeviceIcon(session.deviceLabel);

  const statusBadge = useMemo(() => {
    switch (session.status) {
      case 'ACTIVE':
        return (
          <Badge color="teal" variant="light" size="sm">
            Active
          </Badge>
        );
      case 'EXPIRED':
        return (
          <Badge color="gray" variant="light" size="sm">
            Expired
          </Badge>
        );
      case 'REVOKED':
        return (
          <Badge color="red" variant="light" size="sm">
            Revoked
          </Badge>
        );
      case 'COMPROMISED':
        return (
          <Badge color="red" variant="filled" size="sm">
            Compromised
          </Badge>
        );
      default:
        return (
          <Badge color="gray" variant="outline" size="sm">
            {session.status}
          </Badge>
        );
    }
  }, [session.status]);

  return (
    <article
      className={`${classes.sessionCard} ${session.current ? classes.sessionCardCurrent : ''} ${isHistory ? classes.sessionCardHistory : ''}`}
      aria-label={`Session on ${session.deviceLabel || 'Unknown Device'}`}>
      {/* 1. Header: Icon, Device Label, Status Badge */}
      <div className={classes.cardHeader}>
        <div className={classes.deviceIdentity}>
          <div className={classes.deviceIconBox} aria-hidden="true">
            <IconComponent size={24} weight="duotone" />
          </div>

          <div className={classes.deviceDetails}>
            <div className={classes.badgesGroup}>
              <Text className={classes.deviceLabel} truncate>
                {session.deviceLabel || 'Web Browser Session'}
              </Text>
              {session.current && (
                <span className={classes.currentSessionIndicator}>
                  <span className={classes.currentPulseDot} aria-hidden="true" />
                  <Badge color="teal" variant="filled" size="xs">
                    This Device
                  </Badge>
                </span>
              )}
            </div>
            <Text className={classes.deviceSubtitle}>
              {session.current ? 'Current active session' : `Family ID: ${session.familyId.slice(0, 8)}...`}
            </Text>
          </div>
        </div>

        <div className={classes.badgesGroup}>{statusBadge}</div>
      </div>

      {/* 2. Metadata Grid */}
      <div className={classes.metaGrid}>
        <div className={classes.metaItem}>
          <span className={classes.metaLabel}>Last Active</span>
          <span className={classes.metaValue}>{session.current ? 'Active now' : formatRelativeTime(session.lastUsedAt)}</span>
        </div>

        <div className={classes.metaItem}>
          <span className={classes.metaLabel}>Signed In</span>
          <span className={classes.metaValue}>{formatDate(session.createdAt)}</span>
        </div>

        <div className={classes.metaItem}>
          <span className={classes.metaLabel}>{session.status === 'ACTIVE' ? 'Expires' : 'Ended'}</span>
          <span className={classes.metaValue}>{session.endedAt ? formatDate(session.endedAt) : formatDate(session.expiresAt)}</span>
        </div>
      </div>

      {/* 3. Expandable Technical Details */}
      {detailsOpen && (
        <div className={classes.technicalPanel}>
          <div className={classes.techRow}>
            <span>Family ID:</span>
            <Group gap={6}>
              <span>{session.familyId}</span>
              <CopyButton value={session.familyId} timeout={2000}>
                {({ copied, copy }) => (
                  <Tooltip label={copied ? 'Copied' : 'Copy Family ID'}>
                    <UnstyledButton onClick={copy} aria-label="Copy Family ID">
                      {copied ? <CheckIcon size={14} color="var(--mantine-color-success)" /> : <CopyIcon size={14} />}
                    </UnstyledButton>
                  </Tooltip>
                )}
              </CopyButton>
            </Group>
          </div>

          <div className={classes.techRow}>
            <span>Latest Generation ID:</span>
            <span>{session.latestGenerationId}</span>
          </div>

          <div className={classes.techRow}>
            <span>Exact Expiry:</span>
            <span>{session.expiresAt}</span>
          </div>

          {session.endedAt && (
            <div className={classes.techRow}>
              <span>Exact Ended Time:</span>
              <span>{session.endedAt}</span>
            </div>
          )}
        </div>
      )}

      {/* 4. Actions: Technical details toggle & Revoke/Signout button */}
      <div className={classes.cardActions}>
        <UnstyledButton className={classes.technicalToggle} onClick={() => setDetailsOpen((prev) => !prev)} aria-expanded={detailsOpen}>
          <Group gap={4}>
            <span>{detailsOpen ? 'Hide technical details' : 'View technical details'}</span>
            {detailsOpen ? <CaretUpIcon size={14} /> : <CaretDownIcon size={14} />}
          </Group>
        </UnstyledButton>

        {session.status === 'ACTIVE' && (
          <div>
            {session.current ? (
              <Button
                color="red"
                variant="subtle"
                size="sm"
                leftSection={<SignOutIcon size={16} weight="bold" />}
                onClick={onSignOutCurrent}
                aria-label="Sign out of this device">
                Sign Out This Device
              </Button>
            ) : (
              <Button
                color="red"
                variant="light"
                size="sm"
                leftSection={<TrashSimpleIcon size={16} weight="bold" />}
                onClick={() => onRevokeClick(session)}
                aria-label={`Revoke session for ${session.deviceLabel || 'device'}`}>
                Revoke Session
              </Button>
            )}
          </div>
        )}
      </div>
    </article>
  );
}

export function SessionsManager() {
  // Queries
  const sessionsQuery = $api.useQuery('get', '/api/v1/auth/sessions');

  // State: Row caps (Load More) and History toggle
  const [showHistory, setShowHistory] = useState(false);
  const [activeLimit, setActiveLimit] = useState(5);
  const [historyLimit, setHistoryLimit] = useState(5);

  // Sorted sessions: Current session always first, then by lastUsedAt / createdAt descending
  const sortedSessions = useMemo(() => {
    if (!sessionsQuery.data) return [];
    return [...sessionsQuery.data].sort((a, b) => {
      // 1. Current session always first
      if (a.current && !b.current) return -1;
      if (!a.current && b.current) return 1;

      // 2. Active sessions before inactive
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1;
      if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1;

      // 3. Newest first
      const timeA = new Date(a.lastUsedAt || a.createdAt).getTime();
      const timeB = new Date(b.lastUsedAt || b.createdAt).getTime();
      return timeB - timeA;
    });
  }, [sessionsQuery.data]);

  // Primary concern: ACTIVE sessions only
  const activeSessions = useMemo(() => {
    return sortedSessions.filter((s) => s.status === 'ACTIVE');
  }, [sortedSessions]);

  // Secondary concern: Terminated / History sessions
  const historySessions = useMemo(() => {
    return sortedSessions.filter((s) => s.status !== 'ACTIVE');
  }, [sortedSessions]);

  // Visible items based on pagination limit
  const visibleActiveSessions = useMemo(() => {
    return activeSessions.slice(0, activeLimit);
  }, [activeSessions, activeLimit]);

  const hasMoreActive = activeSessions.length > activeLimit;

  const visibleHistorySessions = useMemo(() => {
    return historySessions.slice(0, historyLimit);
  }, [historySessions, historyLimit]);

  const hasMoreHistory = historySessions.length > historyLimit;

  const otherActiveSessions = useMemo(() => {
    return activeSessions.filter((s) => !s.current);
  }, [activeSessions]);

  const currentSession = useMemo(() => {
    return activeSessions.find((s) => s.current);
  }, [activeSessions]);

  // Handle single revocation
  function handleRevokePrompt(session: DeviceSession) {
    RevokeSessionOverlay.open({
      familyId: session.familyId,
      deviceLabel: session.deviceLabel,
      isCurrent: Boolean(session.current)
    });
  }

  // Handle terminate other sessions
  function handleTerminateOthers() {
    TerminateOthersOverlay.open({ familyIds: otherActiveSessions.map((session) => session.familyId) });
  }

  // Handle signing out the current device
  function handleSignOutCurrent() {
    SignOutCurrentOverlay.open();
  }

  return (
    <section className={classes.container} aria-labelledby="sessions-title">
      {/* 1. Header & Actions Bar */}
      <div className={classes.headerRow}>
        <div className={classes.headerText}>
          <div className={classes.titleWithBadge}>
            <Title order={2} id="sessions-title" className="app-section-title">
              Active Devices & Sessions
            </Title>
            {sessionsQuery.data && (
              <Badge color="teal" variant="light" size="md">
                {activeSessions.length} active
              </Badge>
            )}
          </div>
          <Text size="sm" c="dimmed">
            Manage authorized devices currently connected to your account.
          </Text>
        </div>

        <div className={classes.actionsBar}>
          <Button
            variant="default"
            size="sm"
            leftSection={<ArrowClockwiseIcon size={16} />}
            loading={sessionsQuery.isFetching}
            onClick={() => sessionsQuery.refetch()}
            aria-label="Refresh session list">
            Refresh
          </Button>

          <Button
            color="red"
            variant="light"
            size="sm"
            leftSection={<ShieldWarningIcon size={16} weight="bold" />}
            disabled={otherActiveSessions.length === 0}
            onClick={handleTerminateOthers}
            aria-label="Terminate all other active sessions">
            Terminate Other Sessions
            {otherActiveSessions.length > 0 && ` (${otherActiveSessions.length})`}
          </Button>
        </div>
      </div>

      {/* 2. Overview Stat Cards */}
      <div className={classes.statGrid}>
        <div className={classes.statCard}>
          <div className={classes.statIconWrap} aria-hidden="true">
            <DevicesIcon size={24} weight="duotone" />
          </div>
          <div className={classes.statContent}>
            <Text size="xs" c="dimmed">
              Total Active Devices
            </Text>
            <Text size="lg" fw={700}>
              {sessionsQuery.isLoading ? <Loader size="xs" /> : activeSessions.length}
            </Text>
          </div>
        </div>

        <div className={classes.statCard}>
          <div className={classes.statIconWrap} aria-hidden="true">
            <ShieldCheckIcon size={24} weight="duotone" />
          </div>
          <div className={classes.statContent}>
            <Text size="xs" c="dimmed">
              This Device
            </Text>
            <Text size="sm" fw={600} truncate>
              {currentSession?.deviceLabel || 'Web Browser'}
            </Text>
          </div>
        </div>
      </div>

      {/* 3. Query States */}
      {sessionsQuery.isError && (
        <Alert icon={<WarningCircleIcon size={20} />} title="Could not load sessions" color="red" variant="light">
          <Stack gap="xs">
            <Text size="sm">We encountered an issue retrieving your active sessions. Please verify your connection.</Text>
            <Button size="xs" variant="outline" color="red" onClick={() => sessionsQuery.refetch()}>
              Retry
            </Button>
          </Stack>
        </Alert>
      )}

      {sessionsQuery.isLoading && (
        <div className={classes.sessionsList}>
          <Skeleton height={140} radius="md" />
          <Skeleton height={140} radius="md" />
        </div>
      )}

      {/* 4. Active Sessions List (Primary Concern) */}
      {!sessionsQuery.isLoading && !sessionsQuery.isError && (
        <div className={classes.sessionsList}>
          {activeSessions.length === 0 ? (
            <div className={classes.emptyCard}>
              <div className={classes.emptyIcon} aria-hidden="true">
                <DevicesIcon size={48} weight="light" />
              </div>
              <Title order={3} size="h4">
                No active sessions found
              </Title>
              <Text size="sm" c="dimmed">
                No active device sessions were returned for your account.
              </Text>
              <Button variant="default" size="sm" onClick={() => sessionsQuery.refetch()}>
                Check Again
              </Button>
            </div>
          ) : (
            <>
              {visibleActiveSessions.map((session) => (
                <SessionCard
                  key={session.familyId}
                  session={session}
                  onRevokeClick={handleRevokePrompt}
                  onSignOutCurrent={handleSignOutCurrent}
                />
              ))}

              {/* Load more active sessions if more than cap */}
              {hasMoreActive && (
                <Button
                  variant="default"
                  size="md"
                  fullWidth
                  className={classes.loadMoreButton}
                  onClick={() => setActiveLimit((prev) => prev + 5)}>
                  Show More Active Devices ({activeSessions.length - activeLimit} remaining)
                </Button>
              )}
            </>
          )}
        </div>
      )}

      {/* 5. Progressive Disclosure: Toggle for Terminated Sessions History */}
      {!sessionsQuery.isLoading && !sessionsQuery.isError && historySessions.length > 0 && (
        <div className={classes.historyToggleCard}>
          <div className={classes.historyToggleText}>
            <Text size="sm" fw={500}>
              Terminated Sessions History
            </Text>
            <Text size="xs" c="dimmed">
              {showHistory
                ? `Showing ${visibleHistorySessions.length} of ${historySessions.length} inactive sessions`
                : `View ${historySessions.length} past or signed-out session(s)`}
            </Text>
          </div>
          <Switch
            checked={showHistory}
            onChange={(e) => {
              setShowHistory(e.currentTarget.checked);
              if (e.currentTarget.checked) {
                setHistoryLimit(5);
              }
            }}
            aria-label="Toggle display of terminated session history"
            size="md"
            color="brand"
          />
        </div>
      )}

      {/* 6. Terminated Sessions List (Only visible when user explicitly toggles it) */}
      {showHistory && historySessions.length > 0 && (
        <div className={classes.sessionsList}>
          <div className={classes.sectionSubheading}>
            <span>Inactive & Terminated Devices ({historySessions.length})</span>
          </div>

          {visibleHistorySessions.map((session) => (
            <SessionCard
              key={session.familyId}
              session={session}
              onRevokeClick={handleRevokePrompt}
              onSignOutCurrent={handleSignOutCurrent}
              isHistory
            />
          ))}

          {/* Load more history if more than cap */}
          {hasMoreHistory && (
            <Button
              variant="default"
              size="md"
              fullWidth
              className={classes.loadMoreButton}
              onClick={() => setHistoryLimit((prev) => prev + 5)}>
              Show More History ({historySessions.length - historyLimit} remaining)
            </Button>
          )}
        </div>
      )}
    </section>
  );
}
