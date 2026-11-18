package com.cccece.picharbor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import java.text.BreakIterator
import java.util.Locale

/** Keep the extension plus nearby stem characters; never split a Unicode character boundary. */
internal fun ellipsizeFilename(name:String,width:Float,measure:(String)->Float):String {
    if(width<=0)return ""
    if(name.isEmpty()||measure(name)<=width)return name
    val iterator=BreakIterator.getCharacterInstance(Locale.ROOT).apply{setText(name)}
    val boundaries=mutableListOf(0)
    var end=iterator.next()
    while(end!=BreakIterator.DONE){boundaries+=end;end=iterator.next()}
    val count=boundaries.size-1
    if(count<2)return name
    val dot=name.lastIndexOf('.').takeIf{it>0&&it<name.lastIndex}
    val extension=dot?.let{position->count-boundaries.indexOfLast{it<=position}}?:0
    var tailMin=(extension+8).coerceIn(1,count-1)
    fun candidate(kept:Int):String {
        val tail=maxOf(tailMin,(kept+2)/3).coerceAtMost(kept-1)
        return name.substring(0,boundaries[kept-tail])+"..."+name.substring(boundaries[count-tail])
    }
    while(tailMin>1&&measure(name.substring(0,boundaries[1])+"..."+name.substring(boundaries[count-tailMin]))>width)tailMin--
    var low=2;var high=count-1;var best=candidate(2)
    if(measure(best)>width)return if(measure("...")<=width)"..." else ""
    while(low<=high){val middle=(low+high)/2;val text=candidate(middle);if(measure(text)<=width){best=text;low=middle+1}else high=middle-1}
    return best
}

@Composable
fun FilenameText(name:String,modifier:Modifier=Modifier,style:TextStyle=LocalTextStyle.current) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val displayName=remember(name){name.replace('\n',' ').replace('\r',' ').replace('\t',' ')}
    BoxWithConstraints(modifier) {
        val width=if(constraints.hasBoundedWidth)constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        val text=remember(displayName,width,style,density.fontScale,measurer){ellipsizeFilename(displayName,width){measurer.measure(it,style=style,maxLines=1,softWrap=false).size.width.toFloat()}}
        LText(text,modifier=Modifier.semantics{contentDescription=name},style=style,localize=false,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
    }
}

@Composable
fun FilenameSheet(name:String,onDismiss:()->Unit) {
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    ProductBottomSheet("完整文件名",onDismiss) {
        androidx.compose.foundation.text.selection.SelectionContainer{LText(name,localize=false)}
        TextButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(name))}){LText("复制文件名")}
    }
}
