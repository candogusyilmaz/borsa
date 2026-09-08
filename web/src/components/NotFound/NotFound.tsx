import { Container, Text } from '@mantine/core';
import { Link } from '@tanstack/react-router';
import { Illustration } from './Illustration';
import classes from './NotFound.module.css';

export function NotFound() {
  return (
    <Container className={classes.root}>
      <div className={classes.inner}>
        <Illustration className={classes.image} />
        <div className={classes.content}>
          <div className={classes.title}>Nothing to see here</div>
          <Text c="dimmed" size="md" ta="center" className={classes.description}>
            Page you are trying to open does not exist. You may have mistyped the address, or the page has been moved to another URL. If you
            think this is an error contact support.
          </Text>
          <Link to="/dashboard" className={classes.button}>
            Take me back to home page
          </Link>
        </div>
      </div>
    </Container>
  );
}
