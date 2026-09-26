/** 配置：全部来自环境变量，无配置文件。 */
export const config = {
  port: Number(process.env.PORT || 8787),
  dbFile: process.env.DB_FILE || './data/users.json',

  pluginBaseUrl: process.env.PLUGIN_BASE_URL || 'http://127.0.0.1:17890',
  pluginApiKey: process.env.PLUGIN_API_KEY || 'CHANGE-ME-PLEASE-USE-A-LONG-RANDOM-SECRET-STRING',

  adMode: (process.env.AD_MODE || 'mock').toLowerCase(),
  adRewardAmount: Number(process.env.AD_REWARD_AMOUNT || 50),

  admobSsvSecret: process.env.ADMOB_SSV_SECRET || '',
  pangleAppId: process.env.PANGLE_APP_ID || '',
  pangleSdkSecret: process.env.PANGLE_SDK_SECRET || '',
};
