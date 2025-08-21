import { isElderMode } from './accessibility'

export default {
  data() {
    return {
      elderMode: false
    }
  },
  onShow() {
    this.elderMode = isElderMode()
  }
}
