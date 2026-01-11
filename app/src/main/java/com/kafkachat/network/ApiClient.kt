package com.kafkachat.network

import android.content.Context
import com.kafkachat.util.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String? = null
    private var currentToken: String? = null

    fun getClient(context: Context, token: String? = null): ApiService {
        val baseUrl = Constants.getBaseUrl(context)
        
        // Recreate Retrofit if URL or token changed
        if (retrofit == null || currentBaseUrl != baseUrl || currentToken != token) {
            currentBaseUrl = baseUrl
            currentToken = token
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .apply {
                    if (!token.isNullOrEmpty()) {
                        addInterceptor { chain ->
                            val originalRequest = chain.request()
                            val request = originalRequest.newBuilder()
                                .header("Authorization", "Bearer $token")
                                .build()
                            chain.proceed(request)
                        }
                    }
                }
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofit!!.create(ApiService::class.java)
    }
    
    fun reset() {
        retrofit = null
        currentBaseUrl = null
        currentToken = null
    }
}