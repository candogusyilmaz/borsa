import { Trans } from '@lingui/react/macro';
import { Container, Text } from '@mantine/core';
import { Link } from '@tanstack/react-router';
import { Illustration } from './illustration';
import classes from './not-found.module.css';

export function NotFound() {
  return (
    <Container className={classes.root}>
      <div className={classes.inner}>
        <Illustration className={classes.image} />
        <div className={classes.content}>
          <div className={classes.title}>
            <Trans>Nothing to see here</Trans>
          </div>
          <Text c="dimmed" size="md" ta="center" className={classes.description}>
            <Trans>
              Page you are trying to open does not exist. You may have mistyped the address, or the page has been moved to another URL. If
              you think this is an error contact support.
            </Trans>
          </Text>
          <Link to="/dashboard" className={classes.button}>
            <Trans>Take me back to home page</Trans>
          </Link>
        </div>
      </div>
    </Container>
  );
}
