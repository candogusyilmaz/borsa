import { Button, Card, Group, Stack, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ChartLineUpIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { useState } from 'react';
import { normalizeError } from '@/api/client';
import { PasswordField, TextField } from '@/shared/components/fields';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { useAuth } from '@/shared/hooks/use-auth';
import classes from './login.module.css';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as { redirect?: string };
  const [isSubmitting, setIsSubmitting] = useState(false);

  const form = useForm({
    defaultValues: {
      email: 'test@example.com',
      password: 'Password1234!'
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        await login(value);
        await navigate({ to: search.redirect || '/' });
      } catch (err) {
        const apiError = normalizeError(err);
        notifications.show({
          title: 'Authentication error',
          message: apiError.message || 'Invalid credentials or connection error.',
          color: 'red'
        });
      } finally {
        setIsSubmitting(false);
      }
    }
  });

  return (
    <div className={classes.container}>
      <Card className={classes.card} withBorder shadow="sm">
        <div className={classes.header}>
          <div className={classes.logo}>
            <ChartLineUpIcon size={28} weight="bold" />
          </div>
          <Title order={2}>Sign in to Stocks</Title>
          <Text c="dimmed" size="sm" mt={4}>
            Enter your credentials to access your account
          </Text>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            form.handleSubmit();
          }}
          className={classes.form}>
          <Stack gap="md">
            <form.Field
              name="email"
              validators={{
                onChange: ({ value }) => (!value ? 'Email is required' : undefined)
              }}>
              {(field) => <TextField field={field} label="Email address" placeholder="name@example.com" autoComplete="email" required />}
            </form.Field>

            <form.Field
              name="password"
              validators={{
                onChange: ({ value }) => (!value ? 'Password is required' : undefined)
              }}>
              {(field) => (
                <PasswordField field={field} label="Password" placeholder="••••••••••••" autoComplete="current-password" required />
              )}
            </form.Field>

            <Button type="submit" fullWidth loading={isSubmitting} mt="sm">
              Sign in
            </Button>
          </Stack>
        </form>

        <Group justify="center" mt="xl">
          <ThemeToggle />
        </Group>
      </Card>
    </div>
  );
}
