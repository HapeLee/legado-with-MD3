import { defineConfig } from 'vitepress'

export default defineConfig({
  title: '阅读',
  description: 'Legado-with-MD3 使用文档',
  base: '/legado-with-MD3/',
  head: [
    ['link', { rel: 'icon', href: '/legado-with-MD3/favicon.ico' }]
  ],
  themeConfig: {
    logo: '/logo.png',
    siteTitle: 'Legado Docs',
    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/guide/' },
      { text: '开发文档', link: '/dev/' },
    ],
    sidebar: {
      '/guide/': [
        {
          text: '使用指南',
          items: [
            { text: '帮助文档', link: '/guide/' },
            { text: '阅读界面', link: '/guide/reading' },
            { text: '导入源管理', link: '/guide/book-source' },
            { text: '订阅源管理', link: '/guide/rss-source' },
            { text: '替换规则', link: '/guide/replace-rule' },
          ],
        },
        {
          text: '教程',
          items: [
            { text: 'WebDAV 备份教程', link: '/tutorial/webdav-backup' },
            { text: 'WebDAV 书籍同步', link: '/tutorial/webdav-book' },
          ],
        },
      ],
      '/tutorial/': [
        {
          text: '使用指南',
          items: [
            { text: '帮助文档', link: '/guide/' },
            { text: '阅读界面', link: '/guide/reading' },
            { text: '导入源管理', link: '/guide/book-source' },
            { text: '订阅源管理', link: '/guide/rss-source' },
            { text: '替换规则', link: '/guide/replace-rule' },
          ],
        },
        {
          text: '教程',
          items: [
            { text: 'WebDAV 备份教程', link: '/tutorial/webdav-backup' },
            { text: 'WebDAV 书籍同步', link: '/tutorial/webdav-book' },
          ],
        },
      ],
      '/dev/': [
        {
          text: '入门',
          items: [
            { text: '规则语法详解', link: '/dev/syntax' },
            { text: 'URL 参数详解', link: '/dev/url-options' },
            { text: '源字段速查', link: '/dev/source-fields' },
            { text: '源示例', link: '/dev/examples' },
          ],
        },
        {
          text: '参考',
          items: [
            { text: '源规则帮助', link: '/dev/rule' },
            { text: 'JS 变量和函数', link: '/dev/js' },
            { text: 'XPath 路径表达式', link: '/dev/xpath' },
            { text: '正则表达式', link: '/dev/regex' },
          ],
        },
        {
          text: '配置规范',
          items: [
            { text: '请求头配置', link: '/dev/request-headers' },
            { text: '认证与登录', link: '/dev/authentication' },
            { text: '发现 URL 配置', link: '/dev/discovery-url' },
            { text: '首页模块配置', link: '/spec/homepage-modules' },
            { text: '关联书籍配置', link: '/spec/related-books' },
          ],
        },
        {
          text: '扩展功能',
          items: [
            { text: '源调试', link: '/dev/debug' },
            { text: '字典规则', link: '/dev/dict-rule' },
            { text: '在线朗读规则', link: '/dev/tts-rule' },
            { text: 'TXT 目录正则', link: '/dev/txt-toc' },
            { text: 'MIME 类型参考', link: '/spec/mime-types' },
          ],
        },
      ],
      '/spec/': [
        {
          text: '入门',
          items: [
            { text: '规则语法详解', link: '/dev/syntax' },
            { text: 'URL 参数详解', link: '/dev/url-options' },
            { text: '源字段速查', link: '/dev/source-fields' },
            { text: '源示例', link: '/dev/examples' },
          ],
        },
        {
          text: '参考',
          items: [
            { text: '源规则帮助', link: '/dev/rule' },
            { text: 'JS 变量和函数', link: '/dev/js' },
            { text: 'XPath 路径表达式', link: '/dev/xpath' },
            { text: '正则表达式', link: '/dev/regex' },
          ],
        },
        {
          text: '配置规范',
          items: [
            { text: '请求头配置', link: '/dev/request-headers' },
            { text: '认证与登录', link: '/dev/authentication' },
            { text: '发现 URL 配置', link: '/dev/discovery-url' },
            { text: '首页模块配置', link: '/spec/homepage-modules' },
            { text: '关联书籍配置', link: '/spec/related-books' },
          ],
        },
        {
          text: '扩展功能',
          items: [
            { text: '源调试', link: '/dev/debug' },
            { text: '字典规则', link: '/dev/dict-rule' },
            { text: '在线朗读规则', link: '/dev/tts-rule' },
            { text: 'TXT 目录正则', link: '/dev/txt-toc' },
            { text: 'MIME 类型参考', link: '/spec/mime-types' },
          ],
        },
      ],
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/HapeLee/legado-with-MD3' },
    ],
    footer: {
      message: '基于 Apache-2.0 许可发布',
      copyright: 'Copyright © 2026 Legado',
    },
    search: {
      provider: 'local',
    },
    outline: {
      level: [2, 3],
      label: '页面导航',
    },
    docFooter: {
      prev: '上一页',
      next: '下一页',
    },
    lastUpdated: {
      text: '最后更新于',
    },
    editLink: {
      pattern: 'https://github.com/HapeLee/legado-with-MD3/edit/main/docs/:path',
      text: '在 GitHub 上编辑此页面',
    },
  },
  lastUpdated: true,
  markdown: {
    lineNumbers: true,
  },
})
