import { loadConfig, startupPlan } from './lib/config.mjs'

try {
  process.stdout.write(JSON.stringify(startupPlan(loadConfig(), process.argv[2] ?? 'all', process.argv[3] ?? 'Demo')))
} catch (error) {
  process.stderr.write(`${error.message}\n`)
  process.exitCode = 1
}
