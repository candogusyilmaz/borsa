export function getToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') ?? '');

    if (!user || !user.token) {
      throw new Error('TOKEN_NOT_FOUND');
    }

    return user.token;
  } catch (_error) {
    throw new Error('TOKEN_NOT_FOUND');
  }
}
