<script setup>
// 根组件只挂路由出口。
// 页面级布局（侧边栏 + 顶栏）在 LayoutView 里，登录页是独立全屏页，
// 两个"壳"由 router 决定谁渲染——App 本身不关心
</script>

<template>
  <router-view />
</template>
