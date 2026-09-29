#!/usr/bin/env node
/**
 * uniapp 前端导入/导出自检脚本
 *
 * 背景：本项目多次出现"具名导入的名字在 utils 里忘了写 export"导致的构建失败
 * （如 getMember、BASE_URL 各踩过一次）。Vite 在构建时才报错，定位成本高。
 * 本脚本在写代码后即可快速自检，避免带着这类错误去编译。
 *
 * 用法：在 takeout-uniapp 目录执行  node scripts/check-imports.mjs
 */
import fs from 'node:fs'
import path from 'node:path'

const ROOT = process.cwd()
const UTILS_DIR = path.join(ROOT, 'utils')

/** 收集某文件的所有具名导出 */
function collectExports(file) {
  const src = fs.readFileSync(file, 'utf8')
  const named = new Set()
  for (const m of src.matchAll(/^export\s+(?:async\s+)?(?:function|const|let|var)\s+([A-Za-z_$][\w$]*)/gm)) {
    named.add(m[1])
  }
  // export { a, b } 形式
  for (const m of src.matchAll(/^export\s*\{([^}]+)\}/gm)) {
    m[1].split(',').map(s => s.trim().split(/\s+as\s+/).pop().trim()).filter(Boolean).forEach(n => named.add(n))
  }
  return named
}

/** 递归收集源码文件 */
function collectFiles(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'node_modules' || e.name.startsWith('.') || e.name === 'unpackage') continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) collectFiles(p, out)
    else if (/\.(vue|js|mjs)$/.test(e.name)) out.push(p)
  }
  return out
}

const utils = fs.existsSync(UTILS_DIR)
  ? fs.readdirSync(UTILS_DIR).filter(f => f.endsWith('.js')).map(f => path.join(UTILS_DIR, f))
  : []
const exportMap = new Map()
for (const u of utils) exportMap.set(u, collectExports(u))

console.log('=== utils 导出清单 ===')
for (const [u, set] of exportMap) {
  console.log(`  ${path.relative(ROOT, u)} → ${[...set].join(', ') || '(无具名导出)'}`)
}

console.log('\n=== 导入校验 ===')
const files = collectFiles(ROOT)
let problems = 0
let checked = 0
for (const f of files) {
  const src = fs.readFileSync(f, 'utf8')
  // 匹配 import { a, b } from '...'
  for (const m of src.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]([^'"]+)['"]/g)) {
    const spec = m[2]
    if (!spec.includes('utils/') && !spec.startsWith('./') && !spec.startsWith('@/')) continue
    // 找到对应的 utils 文件
    const base = spec.replace(/^@\//, '').replace(/^\.\//, '').replace(/\.js$/, '')
    const target = utils.find(u => {
      const rel = path.relative(ROOT, u).replace(/\\/g, '/').replace(/\.js$/, '')
      return rel === base || rel.endsWith('/' + base.split('/').pop())
    })
    if (!target) continue
    const names = m[1].split(',').map(s => s.trim().split(/\s+as\s+/)[0].trim()).filter(Boolean)
    const missing = names.filter(n => !exportMap.get(target).has(n))
    checked++
    if (missing.length) {
      problems++
      console.log(`  ❌ ${path.relative(ROOT, f)}`)
      console.log(`     从 ${path.relative(ROOT, target)} 导入 [${missing.join(', ')}] —— 该文件未 export 这些名字`)
    }
  }
}

console.log(`\n共校验 ${checked} 处导入`)
if (problems === 0) {
  console.log('结论：所有导入均可解析 ✅')
  process.exit(0)
} else {
  console.log(`结论：发现 ${problems} 处导入错误 ⚠️（请在对应 utils 文件补 export，或在引用处改用默认导入）`)
  process.exit(1)
}
