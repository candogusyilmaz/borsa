export function getToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') ?? '{}');
    return typeof user?.token === 'string' && user.token.length > 0 ? user.token : null;
  } catch {
    return null;
  }
}
