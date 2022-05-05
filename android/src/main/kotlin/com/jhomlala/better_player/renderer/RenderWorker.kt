package com.jhomlala.better_player


import android.opengl.GLES32.*
import android.opengl.GLES11Ext.*
import android.util.Log
import java.nio.FloatBuffer


class RenderWorker {

    lateinit var vertexBuffer4FBO: FloatBuffer;
    lateinit var textureBuffer4FBO: FloatBuffer;

    var _program: Int? = null;

    var openGLProgram: OpenGLProgram;
    var srcTextureId: Int = -1;

    constructor() {
        this.openGLProgram = OpenGLProgram();
    }

    fun setup() {
        setupVBO4FBO()

        var texArr: IntArray = IntArray(1)
        glGenTextures(1, texArr, 0)
        srcTextureId = texArr[0]

        //glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, srcTextureId);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat());
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat());
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE.toFloat());
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE.toFloat());
        checkGlError("Bind OES texture")

        Log.d("Thread %d".format(Thread.currentThread().getId()), "Frame texture name %d".format(srcTextureId))
    }

    fun renderTexture(texture: Int, matrix: FloatArray?) {
        drawTexture(texture, vertexBuffer4FBO, textureBuffer4FBO, matrix);
        //Log.d("Thread %d".format(Thread.currentThread().getId()), "renderTexture")
    }

    fun drawTexture(texture: Int, vertexBuffer: FloatBuffer, textureBuffer: FloatBuffer, matrix: FloatArray?) {
        var program = getProgram();
        glUseProgram(program)

        glActiveTexture(GL_TEXTURE1);
        if (texture >= 0) {
            glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
        } else {
            glBindTexture(GL_TEXTURE_EXTERNAL_OES, srcTextureId);
        }
        checkGlError("Bind OES texture")

        //glReadPixels(0, 0, 1280, 720, )
        
        var texUniform = glGetUniformLocation(program, "Texture0")
        if (texUniform >= 0) {
            glUniform1i(texUniform, 1);
            checkGlError("Set texture uniform")
        }
        var resultMatrix = floatArrayOf(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );

        if(matrix != null) {
            resultMatrix = matrix;
        }
        var _matrixUniform = glGetUniformLocation(program, "matrix")
        if (_matrixUniform >= 0) {
            glUniformMatrix4fv(_matrixUniform, 1, false, resultMatrix, 0)
        }
        checkGlError("Set matrix uniform")

        //var _positionSlot = 0;
        //var _textureSlot = 1;
        var _positionSlot = glGetAttribLocation(program, "Position");
        var _textureSlot = glGetAttribLocation(program, "TextureCoords");

        glEnableVertexAttribArray(_positionSlot);
        glEnableVertexAttribArray(_textureSlot);

        vertexBuffer.position(0);
        glVertexAttribPointer(_positionSlot, 3, GL_FLOAT, false, 0, vertexBuffer);

        textureBuffer.position(0);
        glVertexAttribPointer(_textureSlot, 2, GL_FLOAT, false, 0, textureBuffer);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays")
    }

    // 显示targetFrameBufferTexture
    fun renderWithFXAA(texture: Int, width: Int, height: Int) {
        var program = openGLProgram.getProgramByName("glsl-fxaa");
        glUseProgram(program)

        glActiveTexture(GL_TEXTURE10);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat());
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat());
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE.toFloat());
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE.toFloat());
        checkGlError("Bind OES texture")

        glUniform1i(glGetUniformLocation(program, "Texture0"), 10);
        // 激活纹理单元GL_TEXTURE0，绑定纹理，
        //  GL_TEXTURE0
        // 将 textureSlot 赋值为 0，而 0 与 GL_TEXTURE0 对应，这里如果写 1，上面也要改成 GL_TEXTURE1

        var frameBufferSize = floatArrayOf(width.toFloat(), height.toFloat());

        glUniform2fv(glGetUniformLocation(program, "frameBufSize"), 1, frameBufferSize, 0);

        var _positionSlot = glGetAttribLocation(program, "Position")
        var _textureSlot = glGetAttribLocation(program, "TextureCoords")
        glEnableVertexAttribArray(_positionSlot);
        glEnableVertexAttribArray(_textureSlot);

        vertexBuffer4FBO.position(0);
        // 设置顶点数据
        glVertexAttribPointer(_positionSlot, 3, GL_FLOAT, false, 0, vertexBuffer4FBO);

        textureBuffer4FBO.position(0);
        glVertexAttribPointer(_textureSlot, 2, GL_FLOAT, false, 0, textureBuffer4FBO);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    fun getProgram(): Int {
        if(_program == null) {
            _program = openGLProgram.getProgramOES();
            //_program = openGLProgram.getProgram();
        }
        return _program as Int;
    }

    fun setupVBO4FBO() {
        var w: Float = 1.0f;
        var h: Float = 1.0f;

        var verticesPoints = floatArrayOf(-w,-h,0.0f, w,-h,0.0f, -w,h,0.0f,  w,h,0.0f);
        var texturesPoints = floatArrayOf(0.0f,0.0f, 1.0f,0.0f, 0.0f,1.0f, 1.0f,1.0f);

        vertexBuffer4FBO = BufferUtils.createFloatBuffer(verticesPoints);
        textureBuffer4FBO = BufferUtils.createFloatBuffer(texturesPoints);
    }

    fun checkGlError(op: String) {
        val error: Int = glGetError();
        if (error != GL_NO_ERROR) {
            Log.e("ES30_ERROR", "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }

    fun dispose() {

    }

}