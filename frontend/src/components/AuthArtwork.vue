<script setup>
import AppIcon from './AppIcon.vue'
</script>
<template>
  <section class="auth-story" aria-label="个人云盘介绍">
    <p class="eyebrow">属于你的文件空间</p>
    <h2>
      把每一份灵感，
      <br />
      都放在顺手的地方。
    </h2>
    <p class="story-copy">
      整理文档、保存照片、分享成果。
      <br />
      从一份文件开始，让生活和工作井井有条。
    </p>
    <div class="auth-art" aria-hidden="true">
      <div class="art-window">
        <div class="art-window-bar">
          <i></i>
          <i></i>
          <i></i>
          <span>我的文件</span>
        </div>
        <div class="art-window-body">
          <div class="art-side">
            <b></b>
            <i></i>
            <i></i>
            <i></i>
          </div>
          <div class="art-files">
            <div class="art-folder">
              <AppIcon name="folder" :size="38" />
              <span>项目文档</span>
            </div>
            <div class="art-folder mint">
              <AppIcon name="image" :size="38" />
              <span>生活照片</span>
            </div>
            <div class="art-file-row">
              <AppIcon name="file" />
              <span>每个想法，都值得被保存</span>
              <AppIcon name="check" :size="16" />
            </div>
            <div class="art-file-row">
              <AppIcon name="file" />
              <span>整理好，再出发</span>
              <AppIcon name="check" :size="16" />
            </div>
          </div>
        </div>
      </div>
      <div class="art-upload">
        <span><AppIcon name="upload" /></span>
        <div>
          <b>让文件触手可及</b>
          <small>收纳 · 查找 · 分享</small>
        </div>
      </div>
    </div>
    <div class="story-notes">
      <span>
        <AppIcon name="folder" :size="16" />
        有序收纳
      </span>
      <span>
        <AppIcon name="search" :size="16" />
        快速查找
      </span>
      <span>
        <AppIcon name="share" :size="16" />
        轻松分享
      </span>
    </div>
  </section>
</template>
