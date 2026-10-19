<template>
  <div ref="editor" style="border: 1px solid #ccc;">
    <Toolbar
      style="border-bottom: 1px solid #ccc"
      :editor="editor"
      :default-config="toolbarConfig"
      :mode="editMode"
    />
    <Editor
      v-model="editValue"
      :style="{'height': editorHeight +'px', 'overflow-y': 'hidden'}"
      :default-config="editorConfig"
      :mode="editMode"
      @onCreated="onCreated"
    />
  </div>
</template>

<script>
import {Editor, Toolbar} from '@wangeditor/editor-for-vue'

export default {
  name: 'WangEditor',
  components: { Toolbar, Editor },
  props: {
    value: {
      type: String,
      required: false,
      default: ''
    },
    editorHeight: {
      type: Number,
      required: false,
      default: 420
    }
  },
  data() {
    return {
      toolbarConfig: {},
      editorConfig: { placeholder: '请输入内容...', MENU_CONF: {
        'uploadImage': {
          // 选择文件时的类型限制，默认为 ['image/*'] 。如不想限制，则设置为 []
          allowedFileTypes: ['image/*'],
          // 图片以 base64 格式插入
          base64LimitSize: 10 * 1024 * 1024
        }
      }},
      editMode: 'simple',
      editor: null,
      editValue: null
    }
  },
  watch: {
    editValue(newVal, oldVal) {
      this.$emit('input', newVal)
    }
  },
  mounted() {

  },
  methods: {
    onCreated(editor) {
      this.editor = Object.seal(editor)
      this.editValue = this.value
    }
  }
}
</script>
<style src="@wangeditor/editor/dist/css/style.css"></style>
<style scoped>
.text {
  text-align:left;
}
::v-deep .w-e-text-container {
  height: 420px !important;
}
</style>
