package com.zihuan.specialtext

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatTextView
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan


open class SpecialTextView : AppCompatTextView {
    private var TAG = "SpecialTextView"
    private var mWholeText = ""//完整字符串
    private lateinit var mWholeTextCopy: String//完整字符串

    //    private var isNeedMovementMethod = false//是否需要设置分段点击的方法
    private var mSpannableString: SpannableStringBuilder? = null
    private var mSpecialTextClick: SpecialTextClick? = null
    private var mSpecialTextFirstIndex = false//默认取关键字最后出现的位置
    private var leftMargin = 0
    private var rightMargin = 0
    private var mEnabledClick = false
    private var mUnderline = false
    private var mExtraLength = 0

    //判断当前是否是追加特殊字符
    private var isEndText = false

    //连接模式
    private var connectionMode = false

    private var startHeight = 0//初始高度

    //首次设置的行数
    private val mEndTextLine by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            maxLines
        } else {
            1
        }
    }

    //是否启用伸缩动画
    internal var mDisableAnim = false


    private val specialEntity = ArrayList<SpecialTextEntity>()


    constructor(context: Context) : super(context) {
        initParams()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initParams()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initParams()
    }


    private fun initParams() {
        //        设置单段点击后背景色透明
        highlightColor = Color.TRANSPARENT
        post {
            //左右边距(只算了view距父view的边距，没有算父view距屏幕的边距，使用的时候注意一下)
            var par = layoutParams as ViewGroup.MarginLayoutParams
            leftMargin = par.leftMargin
            rightMargin = par.rightMargin
//            Logger("leftMargin $leftMargin")
        }
    }

    /**
     * 基本用法
     * 设置包含单个特殊字符的方法，既一段文字中只有一个特殊字符串
     * @param wholeString 完整的字符
     * @param special 特殊字符
     * @param color 特殊字符颜色
     * @param enabledClick 是否设置点击事件
     * @param underline 当前字段是否需要下划线 默认不需要
     */
    fun setSingleText(wholeString: String, special: String, color: Int, textSize: Int = 0, enabledClick: Boolean = false, underline: Boolean = false) {
        setMultipleText(wholeString)
        addText(special, color, textSize, enabledClick, underline)
        complete()
    }

    /**
     * 当一段文字中有多个特殊字符串的时候,先调用这个方法,再调用 append
     * 如果 @param wholeString为空的话，将转换为 appendMode 模式</p>
     * @param wholeString 完整的字符串
     * @sample addText
     */
    fun setMultipleText(wholeString: String = ""): TextBuilder {
        if (wholeString.isNotEmpty()) {
            mWholeText = wholeString
            mWholeTextCopy = wholeString
        } else {
            appendMode()
        }
        return TextBuilder(this)
    }


    /**
     * 设置为追加模式
     * 将多个单独的特殊字符拼接起来,适用于没有完整字符的情况 注意和 setTotalText 区分
     */
    private fun appendMode(): TextBuilder {
        specialEntity.clear()
        connectionMode = true
        mSpannableString = null
        mWholeText = ""
        return TextBuilder(this)
    }

    /**
     * 设置监听事件
     * @param specialListener
     */
    fun setSpecialListener(specialListener: SpecialTextClick): SpecialTextView {
        mSpecialTextClick = specialListener
        return this
    }


    //当前的特殊字符
    private var currentSpecial = ""
    private var currentImageStart = -1

    /**
     * @param special 特殊的字符
     * @param color 特殊字符的色值
     * @param enabledClick 是否设置点击事件
     * @param underline 当前字段是否需要下划线 默认不需要
     */
    internal fun addText(special: String, color: Int, textSize: Int = 0, enabledClick: Boolean = false, underline: Boolean = false, bold: Boolean = false): SpecialTextView {
        if (connectionMode) {
            mWholeText += special
            currentImageStart = mWholeText.length
        } else {
            currentImageStart = getSpecialIndexOf(special) + special.length - 1
//            currentImageStart = getStartIndexOf(special) + special.length - 1
        }
        currentSpecial = special
        val entity = SpecialTextEntity(special, specialEntity.size).also {
            it.special = special
            it.color = color
            it.textSize = textSize
            it.enabledClick = enabledClick
            it.underline = underline
            it.bold = bold
        }
        specialEntity.add(entity)
        return this
    }

    private var ellipsizeText = ""
    private var ellipsizeColor = 0
    internal fun setEllipsize(text: String, endTextColor: Int = 0) {
        ellipsizeText = text
        ellipsizeColor = endTextColor
    }

    private var expandText = ""
    private var expandColor = 0
    internal fun setExpand(text: String, color: Int = 0) {
        expandText = text
        expandColor = color
    }

    private var shrinkText = ""
    private var shrinkColor = 0
    internal fun setShrink(text: String, color: Int = 0) {
        shrinkText = text
        shrinkColor = color
    }

    /**
     * 设置可折叠的文字
     * @param endText 特殊字符串
     * @param imgRes 追加在最后的图片
     * @param enabledClick 是否需要点击事件
     * @param underline 是否需要下划线
     * @param extraLength 额外追加的截取长度，比如用两个逗号替换成两个汉字这种情况就需要多截取几个长度
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    internal fun createFoldText(enabledClick: Boolean = false, underline: Boolean = false, extraLength: Int = 0): SpecialTextView {
        val endText = if (!expand) expandText else shrinkText; mEnabledClick = enabledClick; mUnderline = underline; mExtraLength = extraLength; mEndTextLine; isEndText = true
        val endTextColor = if (!expand) expandColor else shrinkColor;
        val allEndText = (if (!expand) ellipsizeText else "") + endText
        //先设置文本否则拿不到宽度和行数
        this.text = mWholeText
        Logger("原始字符串 $mWholeText")
        var targetLine = maxLines - 1
        post {
            if (!expand && lineCount <= maxLines) {
                Logger("不设置末尾值")
                return@post
            }
            var wholeLen = paint.measureText(mWholeText)
            Logger("设置的最大行数 $maxLines 实际行数 $lineCount 字符串实际宽度 $wholeLen")
            var lineGreaterMax = if (lineCount < maxLines) {//如果实际行数小于设置的最大行数，设置到实际的最后一行
                Logger("实际行数小于设置的最大行数")
                targetLine = lineCount - 1
                false
            } else true
            var targetLineText = mWholeText.substring(layout.getLineStart(targetLine), layout.getLineEnd(targetLine))
            Logger("目标字符串 $targetLineText")
            //如果目标行大于当前屏幕宽度就减去当前行的N个字符+extra N是最后要显示的特殊字符+extra是给图片预留的空间,可以自定义占几个字符宽度，如果图片大可以多占，反之少占
            val width = widthPixels
            //获取当前行的实际宽度（能看到的内容的宽度，不算没有显示出来的）
            val endLineWidth = layout.getLineWidth(targetLine)
            //测量追加字符在当前view的配置下的实际宽度加上末尾图片的宽度
            var textLineWidth = paint.measureText(allEndText)
            //图片的宽度是追加文字的几倍
            //view的宽度相当于match_parent
            var textLen = if (wholeLen > width) {//如果字符宽度大于屏幕宽度
                endLineWidth.plus(width.minus(getWidth())).toInt()
            } else {
                endLineWidth.plus(leftMargin).plus(rightMargin).toInt()
            }
            Logger("当前行所占宽度 $endLineWidth 合计宽度 $textLen")
            //如果当前行所剩宽度小于textLineWidth实际宽度 切割掉textPlusImgLen个字符(前提是实际行大于设置的最大行，否则直接拼接)
            var textPlusImgLen = 0//追加的字符和图片的宽度
            if (width.minus(textLen) < textLineWidth && lineGreaterMax) {
                //追加文字和图片所占长度
                textPlusImgLen = allEndText.length.plus(extraLength)
            }
            val maxTextEndIndex = layout.getLineEnd(targetLine)//当先最后显示的字符的位置
            mWholeText = mWholeText.substring(0, maxTextEndIndex - textPlusImgLen)
            cutEnter()
            Logger("目标行切割后 $mWholeText")
            mWholeText += allEndText
            Logger("目标行拼接后 $mWholeText")
            setEndImg(endText, endTextColor, enabledClick, underline, extraLength)
            if (startHeight == 0) {
                startHeight = measuredHeight
            }
            Logger("高度高度高度 $measuredHeight ")
            if (curClick)
                if (expand) {
                    animatorStart()
                } else {
                    animatorReverse()
                }
        }
        return this
    }


    private fun setEndImg(text: String, color: Int, enabledClick: Boolean = false, underline: Boolean = false, extraLength: Int = 1) {
        specialEntity.clear()
        //取最后一次出现的位置
        setLastIndexOf()
        if (ellipsizeText.isNotEmpty()) {
            val ellColor = if (ellipsizeColor != 0) context.resources.getColor(ellipsizeColor) else currentTextColor
            addText(ellipsizeText, ellColor)
        }
        addText(text, color, enabledClick = enabledClick, underline = underline)
        complete()
    }

    /**
     *
     * 将某个位置的文字替换成图片,默认将最后一个字替换成图片
     * 优化一下以上一步的最后位置为起始位置，调用了此方法 默认在最后追加两个空格
     * @param res 图片资源
     * @param start 图片所占开始位置
     * @param end 图片所占结束位置
     * @param enabledClick 是否设置点击事件
     * 点击后返回 图片的资源id 以此判断点击的位置
     */
    internal fun addImage(res: Int, start2: Int = -1, end: Int = -1, enabledClick: Boolean = false): SpecialTextView {
        var start = start2
        var end = end
        if (start == -1) {
            start = currentImageStart
            //如果mWholeText 不包含currentSpecial 默认追加到最后
            val len = mWholeText.length
            if (start < 0) {
                start = mWholeText.length.minus(1)
            }
            end = start + 1
        }
        val entity = SpecialTextEntity(currentSpecial, specialEntity.size).also {
            it.res = res
            it.startAuto = start2 < 0
            it.start = start
            it.end = end
            it.enabledClick = enabledClick
            it.type = it.TYPE_IMAGE
        }
        specialEntity.add(entity)
        return this
    }

    /**
     * 为指定的文字设置特殊的背景色
     * 注意：如果设置的图片的高度大于文字的高度，背景的高度会以图片的高度为准
     * 如有需要可以重写drawBackGround方法，手动计算 top和bottom
     */
    internal fun addBackground(res: Int, special: String, height: Int = 0, width: Int = 0, textColor: Int = 0): SpecialTextView {
        var start = getSpecialIndexOf(special)
        if (start < 0) return this
        var end = start + special.length
//        if (start < 0) return this
        val entity = SpecialTextEntity(special, specialEntity.size).also {
            it.res = res
            it.start = start
            it.end = end
            it.height = height
            it.width = width
            it.bgColor = textColor
            it.type = it.TYPE_BACKGROUND
        }
        specialEntity.add(entity)
        return this
    }


    private fun setSpecial(special: String, color: Int, textSize: Int = 0, startInt: Int = -1, bold: Boolean) {
        if (TextUtils.isEmpty(mWholeText)) {//要设置最后出现的位置
            Log.e(TAG, "mWholeText为空>>>> 请先设置mWholeText")
            return
        }
//        if (connectionMode) {
//            mSpannableString?.append(special)
//        }
        val start = if (startInt == -1) getSpecialIndexOf(special) else startInt
        val end = start + special.length
        try {
            mSpannableString?.setSpan(ForegroundColorSpan(resources.getColor(color)), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            if (textSize != 0) {
                mSpannableString?.setSpan(AbsoluteSizeSpan(textSize, true), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
            if (bold) {
                mSpannableString?.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
        } catch (e: Exception) {
            Logger("没有发现当前字符>>>> $special  $e")
        }
    }

    private fun setImg(res: Int, start: Int = -1, end: Int = -1) {
        //        如果不想要居中对齐 可以用系统的ImageSpan 提供了两种对齐方式，ImageSpan.ALIGN_BASELINE、ALIGN_BOTTOM
        val imageSpan = SpecialImageSpan(context, res)
        mSpannableString?.setSpan(imageSpan, start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
    }

    private fun setBg(resId: Int, textColor: Int, start: Int, end: Int, height: Int = 0, width: Int = 0) {
        var bg = BackGroundImageSpan(resId, resources.getDrawable(resId))
        if (height != 0) {
            bg.setHeight(height)
        }
        if (width != 0) {
            bg.setWidth(width)
        }
        if (textColor != 0) {
            bg.setColor(resources.getColor(textColor))
        }
        mSpannableString?.setSpan(bg, start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }

    /**
     * 点击事件方法
     * @param enabledClick 是否开启点击事件
     * @param special 特殊字符串
     * @param start 点击事件开始的位置
     * @param end 点击事件结束的位置
     * @param underline 是否需要下划线
     */
    private fun setSpecialClick(enabledClick: Boolean = false, special: String, start1: Int = -1, end: Int = -1, underline: Boolean = false): SpecialTextView {
        if (!enabledClick) return this
        if (mSpecialTextClick == null) {
            if (context is SpecialTextClick) {
                mSpecialTextClick = context as SpecialTextClick
            } else {
                Logger("没有实现监听事件 SpecialTextClick")
            }
        }
//      计算特殊字符的起始位置
        var start = start1
        var end = end
        if (start == -1) {
            start = getSpecialIndexOf(special)
        }
        if (end == -1) {
            end = start + special.length
        }
        if (start == -1) return this//未找到字符位置
        mSpannableString?.setSpan(object : ClickableSpan() {
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = underline//是否设置下划线
            }

            override fun onClick(widget: View) {
                resetEndText()
                mSpecialTextClick?.specialClick(special)
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        return this
    }


    //递归删除尾部换行符
    private fun cutEnter() {
        if (mWholeText.isNotEmpty()) {
            mWholeText.apply {
                var enter = substring(length - 1, length)
                if (enter == "\n") {
                    Logger("包含回车，去除")
                    mWholeText = substring(0, length - 1)
                    cutEnter()
                }
            }
        }
    }

    var expand = false//是否是展开状态
    private var curClick = false//本次点击事件
    private fun resetEndText() {
        if (isEndText) {
            curClick = true
            setMultipleText(mWholeTextCopy)
            expand = !expand
            if (maxLines == mEndTextLine) {
                text = mWholeTextCopy
                post {
                    maxLines = lineCount
                    createFoldText(mEnabledClick, mUnderline, extraLength = mExtraLength)
                }
            } else {
                maxLines = mEndTextLine
                createFoldText(mEnabledClick, mUnderline, extraLength = mExtraLength)
            }
        }
    }

    private fun animatorStart() {
        curClick = false
        if (mDisableAnim) {
            animator.start()
        }
    }

    private fun animatorReverse() {
        curClick = false
        if (mDisableAnim)
            animator.reverse()

    }

    internal fun setFirstIndexOf(): SpecialTextView {
        mSpecialTextFirstIndex = true
        return this
    }

    internal fun setLastIndexOf(): SpecialTextView {
        mSpecialTextFirstIndex = false
        return this
    }


    private fun getSpecialIndexOf(special: String): Int {
        return if (mSpecialTextFirstIndex) getStartIndexOf(special) else getLastIndexOf(special)
    }

    private fun getStartIndexOf(special: String): Int {
        return mWholeText.indexOf(special)
    }

    private fun getLastIndexOf(special: String): Int {
        return mWholeText.lastIndexOf(special)
    }

    //设置字符串完成
    internal fun complete() {
        //过滤所有的image特殊位
        val insetLength = 1
        specialEntity.filter {
            it.type == it.TYPE_IMAGE && it.startAuto
        }.forEachIndexed { index, entity ->
            val newPos = if (entity.end < mWholeText.length - 1) index else 1
            mWholeText = StringBuilder(mWholeText).apply {
                if (entity.end < mWholeText.length - 1) {
                    insert(entity.end + newPos, " ")
                } else {
                    append(" ")
                }
            }.toString()
            //移动到目标字符串后面的空格
            entity.start += index + insetLength
            //占据所有空格位置
            entity.end = entity.start + insetLength
        }
        getSpannableString()
        specialEntity.forEach {
            it.apply {
                var clickTag = special
                when (type) {
                    TYPE_TEXT -> {
                        setSpecial(special, color, textSize, bold = bold)
                    }
                    TYPE_IMAGE -> {
                        setImg(res, start, end)
                        clickTag = res.toString()
                    }
                    TYPE_BACKGROUND -> {
                        setBg(res, bgColor, start, end, height, width)
                        clickTag = res.toString()
                    }
                }
                setSpecialClick(enabledClick, clickTag, start, end, underline = underline)
            }
        }
        movementMethod = LinkMovementMethod.getInstance()
        text = mSpannableString
    }


    /**
     * 修复recycle中的复用问题
     */
    private fun getSpannableString() {
        if (mSpannableString == null)
            mSpannableString = SpannableStringBuilder(mWholeText)
        else {
            mSpannableString?.clear()
            mSpannableString?.append(mWholeText)
        }
    }

    private var enabledLog = false
    fun setEnabledLog(enabled: Boolean = true): SpecialTextView {
        enabledLog = enabled
        return this
    }

    private fun Logger(msg: String) {
        if (enabledLog || BuildConfig.DEBUG) {
            Log.e(TAG, msg)
        }
    }

    /**
     * 监听事件
     */
    interface SpecialTextClick {
        fun specialClick(tag: String)
    }

    private val animator by lazy {
        ValueAnimator.ofInt(startHeight, measuredHeight).also {
            if (startHeight >= 0) {
                it.addUpdateListener { animation ->
                    val params = layoutParams
                    params.height = animation.animatedValue as Int
                    layoutParams = params
                }
            }
        }
    }

    private val widthPixels by lazy {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getMetrics(dm)
        dm.widthPixels
    }
}
