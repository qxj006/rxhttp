package com.example.httpsender.fragment

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.httpsender.R
import com.example.httpsender.databinding.CoroutineFragmentBinding
import com.example.httpsender.entity.*
import com.example.httpsender.kt.errorMsg
import com.example.httpsender.kt.show
import com.google.gson.Gson
import kotlinx.coroutines.launch
import rxhttp.*
import rxhttp.wrapper.param.RxHttp
import rxhttp.wrapper.param.toResponse
import java.util.*

/**
 * 使用 协程(RxHttp + Await) 发请求
 *
 * ```
 * val user = RxHttp.postXxx("/service/...")
 *     .add("key", "value")
 *     .toClass<User>()
 *     .awaitResult {
 *         val user = it
 *     }.onFailure {
 *         val throwable = it
 *     }
 *```
 *
 * User: ljx
 * Date: 2020/4/24
 * Time: 18:16
 */
class AwaitFragment : BaseFragment<CoroutineFragmentBinding>(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.coroutine_fragment)
    }

    override fun CoroutineFragmentBinding.onViewCreated(savedInstanceState: Bundle?) {
        click = this@AwaitFragment
    }

    //发送Get请求，获取文章列表
    private suspend fun CoroutineFragmentBinding.sendGet(view: View) {
        RxHttp.get("/article/list/0/json")
            .toResponse<PageList<Article>>()
            .awaitResult {
                tvResult.text = Gson().toJson(it)
            }.onFailure {
                tvResult.text = it.errorMsg
                //失败回调
                it.show()
            }
    }

    //发送Post表单请求,根据关键字查询文章
    private suspend fun CoroutineFragmentBinding.sendPostForm(view: View) {
        RxHttp.postForm("/article/query/0/json")
            .add("k", "性能优化")
            .toResponse<PageList<Article>>()
            .awaitResult {
                tvResult.text = Gson().toJson(it)
            }.onFailure {
                tvResult.text = it.errorMsg
                //失败回调
                it.show()
            }
    }

    //发送Post Json请求，此接口不通，通过日志可以看到，发送出去的json对象
    private suspend fun CoroutineFragmentBinding.sendPostJson(view: View) {
        /*
           发送以下User对象
           {"name":"张三","sex":1,"height":180,"weight":70,
           "interest":["羽毛球","游泳"],
           "location":{"latitude":30.7866,"longitude":120.6788},
           "address":{"street":"科技园路.","city":"江苏苏州","country":"中国"}}
         */
        val interestList: MutableList<String> = ArrayList() //爱好
        interestList.add("羽毛球")
        interestList.add("游泳")
        val address = "{\"street\":\"科技园路.\",\"city\":\"江苏苏州\",\"country\":\"中国\"}"
        RxHttp.postJson("/article/list/0/json")
            .add("name", "张三")
            .add("sex", 1)
            .addAll("{\"height\":180,\"weight\":70}") //通过addAll系列方法添加多个参数
            .add("interest", interestList) //添加数组对象
            .add("location", Location(120.6788, 30.7866)) //添加位置对象
            .addJsonElement("address", address) //通过字符串添加一个对象
            .toStr()
            .awaitResult {
                tvResult.text = it
            }.onFailure {
                tvResult.text = it.errorMsg
                //失败回调
                it.show()
            }
    }

    //发送Post JsonArray请求，通过日志可以看到，发送出去的json数组
    private suspend fun CoroutineFragmentBinding.sendPostJsonArray(view: View) {
        /*
           发送以下Json数组
           [{"name":"张三"},{"name":"李四"},{"name":"王五"},{"name":"赵六"},{"name":"杨七"}]
         */
        val names: MutableList<Name?> = ArrayList()
        names.add(Name("赵六"))
        names.add(Name("杨七"))
        RxHttp.postJsonArray("/article/list/0/json")
            .add("name", "张三")
            .add(Name("李四"))
            .addJsonElement("{\"name\":\"王五\"}")
            .addAll(names)
            .toStr()
            .awaitResult {
                tvResult.text = it
            }.onFailure {
                tvResult.text = it.errorMsg
                //失败回调
                it.show()
            }

    }

    //此接口不同，但通过日志可以看到，发送出去的是xml数据，如果收到也是xml数据，则会自动解析为我们指定的对象
    private suspend fun CoroutineFragmentBinding.xmlConverter(view: View) {
        RxHttp.postBody("http://webservices.nextbus.com/service/publicXMLFeed?command=routeConfig&a=sf-muni")
            .setBody(Name("张三"))
            .setXmlConverter()
            .toClass<NewsDataXml>()
            .awaitResult {
                tvResult.text = Gson().toJson(it)
            }.onFailure {
                tvResult.text = it.errorMsg
                //失败回调
                it.show()
            }
    }


    private fun CoroutineFragmentBinding.clearLog(view: View) {
        tvResult.text = ""
        tvResult.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onClick(v: View) {
        mBinding.run {
            lifecycleScope.launch {
                when (v.id) {
                    R.id.sendGet -> sendGet(v)
                    R.id.sendPostForm -> sendPostForm(v)
                    R.id.sendPostJson -> sendPostJson(v)
                    R.id.sendPostJsonArray -> sendPostJsonArray(v)
                    R.id.xmlConverter -> xmlConverter(v)
                    R.id.bt_clear -> clearLog(v)
                }
            }
        }
    }
}