package com.honesty.pool;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.honesty.cache.DiskLruCache;
import com.honesty.cache.DiskLruCache.Snapshot;
import com.honesty.loader.ImageData;
import com.honesty.loader.LoaderConfig;
import com.honesty.utils.Md5Helper;

/**
 * 队列线程，用来获取队列里的网络操作
 * 
 * @author honesty
 **/
public class QueueThread extends Thread {

	private BlockingQueue<ImageData> queueDatas;
	private LoaderConfig loaderConfig;

	public QueueThread(BlockingQueue<ImageData> queueDatas,
			LoaderConfig loaderConfig) {
		this.queueDatas = queueDatas;
		this.loaderConfig = loaderConfig;
	}

	@Override
	public void run() {
		super.run();
		try {
			while (!isInterrupted()) {
				final ImageData queueData = queueDatas.take();
				try {
					// 如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存
					DiskLruCache.Editor editor = loaderConfig.mDiskLruCache
							.edit(Md5Helper.toMD5(queueData.getUrl()));
					if (editor != null) {
						OutputStream outputStream = editor.newOutputStream(0);
						if (Http.downloadUrlToStream(queueData.getUrl(),
								outputStream)) {
							editor.commit();
						} else {
							editor.abort();
						}
					}
					// 缓存被写入后，再次查找key对应的缓存
					Snapshot snapShot = loaderConfig.mDiskLruCache
							.get(Md5Helper.toMD5(queueData.getUrl()));
					FileInputStream fileInputStream = null;
					FileDescriptor fileDescriptor = null;
					if (snapShot != null) {
						fileInputStream = (FileInputStream) snapShot
								.getInputStream(0);
						fileDescriptor = fileInputStream.getFD();
					}
					// 将缓存数据解析成Bitmap对象
					Bitmap bitmap = null;
					if (fileDescriptor != null) {
						final Bitmap bitmap1 = BitmapFactory
								.decodeFileDescriptor(fileDescriptor);
						bitmap = bitmap1;
						if(queueData.getImageView() == null){
							if(loaderConfig.isDebug){
								System.out.println("下载不显示");
							}
						}else{
							//TODO 放到主线程显示图片
							queueData.getImageView().post(new Runnable() {
								@Override
								public void run() {
									if(loaderConfig.isDebug){
										System.out.println("url加载图片显示");
									}
									queueData.getImageView().setImageBitmap(bitmap1);
								}
							});
						}						
					}
					
					if (bitmap != null) {
						// 将Bitmap对象添加到内存缓存当中
						loaderConfig.myLruCache.putLruBitmap(
								Md5Helper.toMD5(queueData.getUrl()), bitmap);
					}
					loaderConfig.mDiskLruCache.flush();  
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
