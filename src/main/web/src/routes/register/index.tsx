import { Button, Divider, Group, PasswordInput, Stack, TextInput } from '@mantine/core';
import { hasLength, isEmail, useForm } from '@mantine/form';
import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';
import { createFileRoute, Link } from '@tanstack/react-router';
import type { AxiosError } from 'axios';
import { CanverseText } from '~/components/CanverseText';
import { useAuthentication } from '~/lib/AuthenticationContext';
import { alerts } from '~/lib/alert';
import styles from '../login/auth.module.css';

export const Route = createFileRoute('/register/')({
  head: () => ({
    meta: [
      {
        title: 'Register | Canverse'
      },
      {
        name: 'description',
        content: 'Create your Canverse account to connect and collaborate seamlessly.'
      },
      {
        name: 'keywords',
        content: 'register, sign up, Canverse, collaboration, account creation'
      },
      {
        name: 'robots',
        content: 'index, follow'
      },
      {
        property: 'og:title',
        content: 'Register | Canverse'
      },
      {
        property: 'og:description',
        content: 'Create your Canverse account to connect and collaborate seamlessly.'
      },
      {
        property: 'og:type',
        content: 'website'
      },
      {
        property: 'og:url',
        content: 'https://borsa.canverse.dev/register'
      },
      {
        property: 'og:image',
        content: 'https://borsa.canverse.dev/assets/favicon.png'
      }
    ]
  }),
  component: RouteComponent
});

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { login, register } = useAuthentication();

  const form = useForm({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: ''
    },
    validate: {
      firstName: hasLength({ min: 2, max: 50 }, 'First name must be between 2 and 50 characters'),
      lastName: hasLength({ min: 2, max: 50 }, 'Last name must be between 2 and 50 characters'),
      email: isEmail('Email is not valid'),
      password: hasLength({ min: 8, max: 20 }, 'Password must be between 8 and 50 characters'),
      confirmPassword: (value, values) => (value !== values.password ? 'Passwords do not match' : null)
    }
  });

  const handleSuccess = async (response: CredentialResponse) => {
    const idToken = response.credential;

    if (!idToken) {
      console.log('idToken not found');
      return;
    }

    login.mutate(
      { token: idToken },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        }
      }
    );
  };

  function handleSubmit(data: { firstName: string; lastName: string; email: string; password: string; confirmPassword: string }): void {
    register.mutate(
      {
        name: `${data.firstName} ${data.lastName}`,
        email: data.email,
        password: data.password
      },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        },
        onError: (error) => {
          const res = error as AxiosError;
          const responseData = res.response?.data as {
            detail?: string;
            'invalid-params'?: { field: string; message: string }[];
          };

          // Handle validation errors with field-specific messages
          if (responseData?.['invalid-params']) {
            for (const param of responseData['invalid-params']) {
              if (param.field === 'name') {
                form.setFieldError('firstName', param.message);
                form.setFieldError('lastName', param.message);
              } else {
                form.setFieldError(param.field, param.message);
              }
            }
          } else {
            alerts.error(responseData?.detail || 'An unknown error occurred while creating your account', 'Error while creating account');
          }
        }
      }
    );
  }

  return (
    <div className={styles.authContainer}>
      <div className={styles.authCard}>
        <div className={styles.authHeader}>
          <h1 className={styles.authTitle}>Create your account</h1>
          <p className={styles.authSubtitle}>
            Connect to <CanverseText span /> with:
          </p>
        </div>
        <div className={styles.googleButtonWrapper}>
          <GoogleLogin
            theme="filled_black"
            width={'409px'}
            size="medium"
            onSuccess={handleSuccess}
            context="signup"
            text="signup_with"
            ux_mode="popup"
            itp_support={true}
            logo_alignment="left"
            useOneTap
          />
        </div>
        <div className={styles.dividerWrapper}>
          <Divider label="OR SIGN UP WITH YOUR EMAIL" />
        </div>
        <form className={styles.formStack} onSubmit={form.onSubmit(handleSubmit)}>
          <Stack gap="md">
            <Group grow align="flex-start">
              <TextInput label="First name" placeholder="John" withAsterisk {...form.getInputProps('firstName')} />
              <TextInput label="Last name" placeholder="Doe" withAsterisk {...form.getInputProps('lastName')} />
            </Group>
            <TextInput label="Email" placeholder="me@canverse.com" withAsterisk {...form.getInputProps('email')} />
            <PasswordInput label="Password" placeholder="********" withAsterisk {...form.getInputProps('password')} />
            <PasswordInput label="Confirm password" placeholder="********" withAsterisk {...form.getInputProps('confirmPassword')} />
            <Button
              className={styles.submitButton}
              loading={login.isPending || register.isPending}
              type="submit"
              fullWidth
              disabled={login.isPending}>
              Sign up
            </Button>
          </Stack>
        </form>
        <div className={styles.authFooter}>
          Already have an account?{' '}
          <Link to="/login" className={styles.authLink}>
            Login
          </Link>
        </div>
      </div>
    </div>
  );
}
