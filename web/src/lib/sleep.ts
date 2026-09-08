export const sleep = (ms = 1) => new Promise((resolve) => setTimeout(resolve, ms));

// biome-ignore lint/suspicious/noExplicitAny: its fine
export const minDelay = async (promise: Promise<any>, ms = 1000) => {
  const [result] = await Promise.all([promise, sleep(ms)]);
  return result;
};
