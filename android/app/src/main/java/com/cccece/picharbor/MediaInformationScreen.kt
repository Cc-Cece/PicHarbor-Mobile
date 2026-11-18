package com.cccece.picharbor

import androidx.compose.runtime.Composable

@Composable
fun MediaInformationScreen(vm:HarborViewModel,data:HarborUiData) {
    ProductBottomSheet("照片详情",vm::back){MediaInformationContent(vm,data)}
}

@Composable
fun MediaInformationContent(vm:HarborViewModel,data:HarborUiData) {
    val media=vm.activeMedia
    if(media==null) {
        EmptyState("没有可显示的项目","调整筛选条件或相册访问权限。")
        return
    }
    val pair=LivePhotoPairing.findPair(media,data.media)
        ProductSection("资源") {
            ProductValue("文件名",media.name,false)
            ProductValue("文件大小",formatSize(media.size))
            ProductValue("类型",media.mime,false)
            ProductValue("所在文件夹",media.folderName,false)
            if(pair!=null){ProductValue("动态视频",pair.name,false);ProductValue("视频大小",formatSize(pair.size))}
        }
        ProductSection("备份") {
            ProductValue("当前状态",data.itemMap[media.id]?.let{itemPhaseLabel(it)}?:phaseLabel(if(data.backed(media))"done" else "queued"))
            data.backupMap[media.id]?.let{ProductValue("备份时的画质设置",qualityName(it.quality))}
        }
}
