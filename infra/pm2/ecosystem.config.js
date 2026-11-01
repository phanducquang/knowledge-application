const path = require("node:path");

const root = path.resolve(process.env.KNOWLEDGE_DEPLOY_ROOT || "/home/ubuntu/knowledge-application");
const common = {
  cwd: root,
  interpreter: "/bin/bash",
  exec_mode: "fork",
  instances: 1,
  autorestart: true,
  watch: false,
  restart_delay: 5000,
  kill_timeout: 45000,
  time: true,
  env: {
    KNOWLEDGE_DEPLOY_ROOT: root,
    KNOWLEDGE_NODE_BIN: process.execPath,
  },
};

module.exports = {
  apps: [
    {
      ...common,
      name: "knowledge-api",
      script: path.join(root, "current/infra/pm2/start-api.sh"),
      out_file: path.join(root, "shared/logs/api-out.log"),
      error_file: path.join(root, "shared/logs/api-error.log"),
    },
    {
      ...common,
      name: "knowledge-web",
      script: path.join(root, "current/infra/pm2/start-web.sh"),
      out_file: path.join(root, "shared/logs/web-out.log"),
      error_file: path.join(root, "shared/logs/web-error.log"),
    },
  ],
};
