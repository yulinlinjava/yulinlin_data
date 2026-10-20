// Node.js 18+。文档导出及静态链接检查，不执行编译或测试。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const docRoot = path.dirname(fileURLToPath(import.meta.url));
const root = path.dirname(docRoot);
const read = file => fs.readFileSync(path.join(root, file), 'utf8').replace(/\r\n/g, '\n');
const topics = JSON.parse(read('doc/topics.json'));
const output = 'doc/AI接入指南.md';
const slug = text => text.replace(/<[^>]+>/g, '').toLowerCase()
  .replace(/[^\p{L}\p{N}\p{M}_\-\s]/gu, '').replace(/\s/g, '-');
function parse(text) {
  let fence = false;
  const headings = [], links = [], ids = new Set(), seen = new Map();
  for (const [line, value] of text.split('\n').entries()) {
    if (/^\s*```/.test(value)) { fence = !fence; continue; }
    if (fence) continue;
    for (const m of value.matchAll(/<a\s+id="([^"]+)"/g)) ids.add(m[1]);
    const match = /^(#{1,6}) (.+)$/.exec(value);
    if (match) {
      const base = slug(match[2]), count = seen.get(base) || 0;
      seen.set(base, count + 1);
      const anchor = base + (count ? `-${count}` : '');
      ids.add(anchor);
      headings.push({line, anchor, level: match[1].length, title: match[2]});
    }
    for (const m of value.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) links.push({target:m[1].replace(/^<|>$/g,''),line:line+1});
  }
  return {headings, links, ids, fence};
}
const listed = topics.flatMap(t=>t.pages.map(p=>'doc/'+p.file));
if (new Set(listed).size !== listed.length) throw new Error('topics.json 存在重复页面');
const exported = topics.filter(t=>t.ai).flatMap(t=>t.pages.map(p=>'doc/'+p.file));
const included = new Set(exported);
const prefix = file => 'ai-' + file.replace(/^doc\//,'').replace(/\.md$/,'').replace(/\//g,'-');
const contents = new Map(listed.map(f=>[f,read(f)]));
const parsed = new Map([...contents].map(([f,t])=>[f,parse(t)]));
const parts = [
  '# yulinlin-data AI 使用指南',
  '> 自动生成；正文来源为 topics.json 中 ai=true 的小专题。不要手工编辑本文件。',
  '适用 JDK 25、Spring Boot 3.5.16、制品 3.0。用于无法读取仓库时的单附件接入；有仓库访问能力时先读 llms.txt，按任务读取小专题即可。内部架构、监控与完整性能报告按需另提供。',
  '代码片段不等于已完成编译或全部数据库集成验证。历史测试结果只代表原日期和环境，本次文档导出不编译、不运行测试。业务必须替换示例表、账号、地址和密钥。'
];
parts.push('## 主题导航\n\n'+topics.filter(t=>t.ai).map(t=>
  `- **${t.title}**：`+t.pages.map(p=>`[${p.title}](#${prefix('doc/'+p.file)})`).join(' · ')).join('\n'));
for (const file of exported) {
  const metadata = parsed.get(file);
  const byLine = new Map(metadata.headings.map(h=>[h.line,h]));
  let fence = false;
  const body = contents.get(file).split('\n').map((value,line)=>{
    if (/^\s*```/.test(value)) { fence = !fence; return value; }
    if (fence) return value;
    let transformed = value.replace(/\[([^\]]*)\]\(([^)]+)\)/g,(whole,label,raw)=>{
      const link = raw.replace(/^<|>$/g,'');
      if (/^[a-z][\w+.-]*:/i.test(link)) return whole;
      const [pathname,anchor] = link.split('#');
      const target = pathname ? path.posix.normalize(path.posix.join(path.posix.dirname(file),decodeURI(pathname))) : file;
      if (included.has(target)) return `[${label}](#${prefix(target)}${anchor?'-'+decodeURI(anchor):''})`;
      return `[${label}](${path.posix.relative('doc',target)}${anchor?'#'+anchor:''})`;
    });
    const h = byLine.get(line);
    if (h) {
      transformed = `<a id="${prefix(file)}-${h.anchor}"></a>\n` +
        transformed.replace(/^#{1,6}/, '#'.repeat(Math.min(h.level+1,6)));
    }
    return transformed;
  }).join('\n');
  parts.push(`<a id="${prefix(file)}"></a>\n<!-- source: ${file} -->\n${body.trim()}`);
}
const expected = parts.join('\n\n---\n\n')+'\n';
if (process.argv.includes('--check')) {
  if (!fs.existsSync(path.join(root,output)) || read(output)!==expected)
    throw new Error('AI 指南已过期，请运行 ./doc/build-ai-docs.ps1');
} else {
  fs.writeFileSync(path.join(root,output),expected);
}
function walk(dir) {
  return fs.readdirSync(path.join(root,dir),{withFileTypes:true}).flatMap(e=>{
    const file = `${dir}/${e.name}`;
    if (e.isDirectory()) return ['target','node_modules','.git'].includes(e.name)?[]:walk(file);
    return e.name.endsWith('.md')?[file]:[];
  });
}
const docs = walk('doc');
for (const file of docs.filter(f=>/^doc\/[^/]+\/[^/]+\.md$/.test(f) && !f.endsWith('/README.md'))) {
  if (!listed.includes(file)) throw new Error(`小专题未登记：${file}`);
}
const files = ['README.md','llms.txt',...docs,...walk('admin')];
const errors = [];
for (const file of files) {
  const info = parse(read(file));
  if (info.fence) errors.push(`${file}: 代码围栏未闭合`);
  for (const {target, line} of info.links) {
    if (/^[a-z][\w+.-]*:/i.test(target)) continue;
    const [pathname,fragment] = target.split('#');
    const dest = pathname?path.posix.normalize(path.posix.join(path.posix.dirname(file),decodeURI(pathname))):file;
    if (!fs.existsSync(path.join(root,dest))) {
      errors.push(`${file}:${line}: 链接目标不存在 ${target}`); continue;
    }
    if (fragment && dest.endsWith('.md')) {
      if (!parsed.has(dest)) parsed.set(dest,parse(read(dest)));
      if (!parsed.get(dest).ids.has(decodeURI(fragment))) errors.push(`${file}:${line}: 章节不存在 ${target}`);
    }
  }
}
if (errors.length) throw new Error(errors.join('\n'));
console.log(`文档完成：${topics.length} 个主题，${listed.length} 篇小专题，${exported.length} 篇导出至 AI 指南；相对路径、章节锚点与代码围栏检查通过。未编译、未运行测试。`);
