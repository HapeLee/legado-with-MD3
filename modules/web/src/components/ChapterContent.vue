<template>
  <div class="title" data-chapterpos="0" ref="titleRef">{{ title }}</div>
  <div
    v-for="(para, index) in contents"
    :key="index"
    ref="paragraphRef"
    :data-chapterpos="para.start"
  >
    <img
      class="full"
      v-if="/^\s*<img[^>]*src[^>]+>$/.test(para.html)"
      :src="getImageSrc(para.html)"
      @error.once="proxyImage"
      loading="lazy"
    />
    <div v-else class="paragraph-text" :style="{ fontFamily, fontSize }" v-html="para.html" />
  </div>
</template>

<script setup lang="ts">
import { isLegadoUrl } from '@/utils/utils'
import API, { type ReaderWebParagraph } from '@api'
import { paragraphAtPosition } from '@/utils/chapterProgress'
import jump from '@/plugins/jump'
import type { webReadConfig } from '@/web'

const store = useBookStore()
const readWidth = computed(() => store.config.readWidth)
const bookUrl = computed(() => store.readingBook.bookUrl)

const props = defineProps<{
  chapterIndex: number
  contents: ReaderWebParagraph[]
  title: string
  spacing: webReadConfig['spacing']
  fontFamily: string
  fontSize: string
}>()

const getImageSrc = (content: string) => {
  const src = new DOMParser().parseFromString(content, 'text/html')
    .querySelector('img')?.getAttribute('src') ?? ''
  if (isLegadoUrl(src))
    return API.getProxyImageUrl(
      bookUrl.value,
      src,
      readWidth.value,
    )
  return src
}
const proxyImage = (event: Event) => {
  ;(event.target as HTMLImageElement).src = API.getProxyImageUrl(
    bookUrl.value,
    (event.target as HTMLImageElement).src,
    readWidth.value,
  )
}

const titleRef = ref<HTMLElement>()
const paragraphRef = ref<HTMLElement[]>([])
const scrollToReadedLength = (length: number) => {
  if (length === 0) return
  const paragraphIndex = paragraphAtPosition(props.contents, length)
  if (paragraphIndex === -1) return
  nextTick(() => {
    jump(paragraphRef.value![paragraphIndex], {
      duration: 0,
    })
  })
}
defineExpose({
  scrollToReadedLength,
})
let intersectionObserver: IntersectionObserver | null = null
const emit = defineEmits(['readedLengthChange'])
onMounted(() => {
  intersectionObserver = new IntersectionObserver(
    entries => {
      for (const { target, isIntersecting } of entries) {
        if (isIntersecting) {
          emit(
            'readedLengthChange',
            props.chapterIndex,
            parseInt((target as HTMLElement).dataset.chapterpos as string),
          )
        }
      }
    },
    {
      rootMargin: `0px 0px -${window.innerHeight - 24}px 0px`,
    },
  )
  intersectionObserver.observe(titleRef.value!)
  paragraphRef.value!.forEach(element => {
    intersectionObserver!.observe(element)
  })
})

onUnmounted(() => {
  intersectionObserver?.disconnect()
  intersectionObserver = null
})
</script>

<style lang="scss" scoped>
.title {
  margin-bottom: 57px;
  font:
    24px / 32px PingFangSC-Regular,
    HelveticaNeue-Light,
    'Helvetica Neue Light',
    'Microsoft YaHei',
    sans-serif;
}

.paragraph-text {
  display: block;
  word-wrap: break-word;
  /*   word-break: break-all; */
  letter-spacing: calc(v-bind('props.spacing.letter') * 1em);
  line-height: calc(1 + v-bind('props.spacing.line'));
  margin: calc(v-bind('props.spacing.paragraph') * 1em) 0;

  :deep(img) {
    height: 1em;
  }
}

.full {
  display: block;
  width: 100%;
}
</style>
