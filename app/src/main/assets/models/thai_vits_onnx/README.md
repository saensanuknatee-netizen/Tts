---
library_name: transformers.js
license: cc-by-nc-4.0
language:
- th
pipeline_tag: text-to-speech
base_model: facebook/mms-tts-tha
---

# traxlate-mms-tts-tha

[facebook/mms-tts-tha](https://huggingface.co/facebook/mms-tts-tha) (Meta MMS
Thai VITS text-to-speech) converted to ONNX for
[transformers.js](https://huggingface.co/docs/transformers.js), laid out
like the Xenova `mms-tts-*` conversions (fp32 + q8 dynamic quantization).
Converted with the transformers.js 3.8.1 conversion script (torch 2.6.0
legacy exporter, onnxslim skipped, fp16 omitted -- see quantize_config.json).

```js
import { pipeline } from '@huggingface/transformers';
const synth = await pipeline('text-to-speech', 'payam1394/traxlate-mms-tts-tha', { dtype: 'q8' });
```

License: CC-BY-NC-4.0 (inherited from the MMS release).
